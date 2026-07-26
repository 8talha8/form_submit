package com.example.seleniumdemo.service;

import com.example.seleniumdemo.model.FieldMapping;
import com.example.seleniumdemo.model.FlowSession;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives a browser and:
 * 1. opens the target form
 * 2. fills each field from data.csv using the mapping.csv locators
 * 3. STOPS before submit leaving the session live for human verification
 * 4. submits (or closes) only when the human asks.
 *
 * Two modes (app.selenium.mode):
 * "local"  -> a real Chrome window opens ON THIS MACHINE (no Docker).
 *             Selenium Manager auto-downloads the driver. The human verifies
 *             and submits directly in that visible window or via the dashboard.
 * "remote" -> talks to Selenoid/Grid and exposes a VNC url for the dashboard.
 */
@Service
public class SeleniumFlowService {

    private static final Logger log = LoggerFactory.getLogger(SeleniumFlowService.class);

    private final MappingService mappingService;
    private final SessionRegistry registry;
    private final String mode;
    private final String seleniumUrl;
    private final String selenoidUiBase;
    private final boolean enableVnc;
    private final String defaultPassword;

    public SeleniumFlowService(MappingService mappingService,
                               SessionRegistry registry,
                               @Value("${app.selenium.mode:local}") String mode,
                               @Value("${app.selenium.remote-url:}") String seleniumUrl,
                               @Value("${app.selenium.selenoid-ui-base:}") String selenoidUiBase,
                               @Value("${app.selenium.enable-vnc:false}") boolean enableVnc,
                               @Value("${app.selenium.default-password:}") String defaultPassword) {
        this.mappingService = mappingService;
        this.registry = registry;
        this.mode = mode;
        this.seleniumUrl = seleniumUrl;
        this.selenoidUiBase = selenoidUiBase;
        this.enableVnc = enableVnc;
        this.defaultPassword = defaultPassword;
    }

    /** Start a flow: open the page and fill it, but do NOT submit. Returns the session. */
    public FlowSession startAndFill(String owner, String targetUrl, int dataRow) throws Exception {
        List<FieldMapping> mappings = mappingService.loadMappings();
        Map<String, String> data = mappingService.getRow(dataRow);

        RemoteWebDriver driver = createDriver();
        String sessionId = UUID.randomUUID().toString();
        String vncUrl = buildVncUrl(driver);
        FlowSession session = new FlowSession(sessionId, owner, driver, vncUrl);

        try {
            registry.register(session);
            log.info("Session {} started for owner={} vnc={}", sessionId, owner, vncUrl);
            grantGeolocationPermission(driver, extractOrigin(targetUrl), sessionId);
            driver.get(targetUrl);

            waitForLoginPage(driver, targetUrl, sessionId);
            fillLoginCredentials(driver, data, sessionId);
            autoSubmitIfNeeded(driver, targetUrl, sessionId);
            waitForPostLoginNavigation(driver, sessionId);
            dismissBlockingModal(driver, sessionId);

            String postLoginUrl = resolvePostLoginUrl(targetUrl);
            if (!postLoginUrl.equals(targetUrl) && !driver.getCurrentUrl().contains("personal_profile.php")) {
                log.info("session={} navigating to post-login target {}", sessionId, postLoginUrl);
                driver.get(postLoginUrl);
            }

            if (driver.getCurrentUrl().contains("Dashboard") || driver.getCurrentUrl().contains("dashboard")) {
                navigateToPersonalProfileFromDashboard(driver, sessionId);
            }

            waitForPersonalProfilePage(driver, sessionId);
            waitForFormControlsToBeInteractive(driver, sessionId);
            fillFields(driver, mappings, data, sessionId);
            session.setStatus(FlowSession.Status.AWAITING_SUBMIT);
        } catch (Exception e) {
            // Fatal error during driver creation/navigation: clean up and rethrow.
            session.setStatus(FlowSession.Status.ERROR);
            registry.remove(sessionId);
            throw e;
        }
        return session;
    }

    private void fillLoginCredentials(RemoteWebDriver driver, Map<String, String> data, String sessionId) {
        fillLoginField(driver, "StudentEmail", getDataValue(data, "Email", "email"), sessionId, "email");
        String password = getDataValue(data, "password", "", "Password");
        if (password == null || password.isBlank()) {
            password = defaultPassword;
        }
        fillLoginField(driver, "StudentPassword", password, sessionId, "password");
    }

    private String getDataValue(Map<String, String> data, String... keys) {
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            String value = data.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void fillLoginField(RemoteWebDriver driver, String fieldId, String value, String sessionId, String columnName) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            WebElement element = driver.findElement(By.id(fieldId));
            element.clear();
            element.sendKeys(value);
            log.info("session={} filled login field {} from column '{}'", sessionId, fieldId, columnName);
        } catch (Exception e) {
            log.warn("session={} could not fill login field '{}' from column '{}': {}",
                sessionId, fieldId, columnName, e.getMessage());
        }
    }

    private void fillFields(RemoteWebDriver driver, List<FieldMapping> mappings, Map<String, String> data, String sessionId) {
        for (FieldMapping mapping : mappings) {
            String value = data.get(mapping.csvColumn());
            if ((value == null || value.isEmpty()) && "PIN CODE" .equals(mapping.csvColumn())) {
                value = "423203";
            }
            if (value == null || value.isEmpty()) {
                log.debug("No data for column '{}', skipping", mapping.csvColumn());
                continue;
            }
            try {
                WebElement element = findElementWithFallback(driver, mapping, sessionId);
                if (element == null) {
                    continue;
                }

                String tag = element.getTagName();
                String type = element.getAttribute("type");

                if ("select".equalsIgnoreCase(tag)) {
                    try {
                        selectDropdownValue(driver, element, value, sessionId, mapping.csvColumn(), mapping.locatorType(), mapping.locatorValue());
                    } catch (Exception ex) {
                        log.warn("session={} could not select value='{}' for column='{}' locator={}:{} error={}",
                            sessionId, value, mapping.csvColumn(), mapping.locatorType(), mapping.locatorValue(), ex.getMessage());
                    }
                } else if ("input".equalsIgnoreCase(tag) && "checkbox".equalsIgnoreCase(type)) {
                    try {
                        boolean should = "1".equals(value) || "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value);
                        if (element.isSelected() != should) {
                            element.click();
                        }
                    } catch (Exception ex) {
                        log.warn("session={} could not set checkbox for column='{}' locator={}:{} error={}",
                            sessionId, mapping.csvColumn(), mapping.locatorType(), mapping.locatorValue(), ex.getMessage());
                    }
                } else {
                    try {
                        element.clear();
                    } catch (Exception ignore) {
                    }
                    element.sendKeys(value);
                    log.debug("Filled {} = {}", mapping.csvColumn(), value);
                }
            } catch (Exception e) {
                log.warn("session={} could not fill column='{}' locator={}:{} error={}",
                    sessionId, mapping.csvColumn(), mapping.locatorType(), mapping.locatorValue(), e.getMessage());
            }
        }
    }

    private void selectDropdownValue(RemoteWebDriver driver, WebElement element, String value, String sessionId, String csvColumn, String locatorType, String locatorValue) {
        if (value == null || value.isBlank()) {
            return;
        }

        String target = value.trim();
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Select select = new Select(element);
                try {
                    select.selectByVisibleText(target);
                    if (isDropdownSelectionPresent(select, target)) {
                        return;
                    }
                } catch (Exception ignored) {
                }

                try {
                    select.selectByValue(target);
                    if (isDropdownSelectionPresent(select, target)) {
                        return;
                    }
                } catch (Exception ignored) {
                }

                if (trySelectViaJavascript(driver, element, target)) {
                    return;
                }
            } catch (Exception ignored) {
            }

            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.warn("session={} could not select dropdown value='{}' for column='{}' locator={}:{}",
            sessionId, value, csvColumn, locatorType, locatorValue);
    }

    private boolean isDropdownSelectionPresent(Select select, String value) {
        try {
            WebElement selected = select.getFirstSelectedOption();
            String selectedText = selected.getText();
            String selectedValue = selected.getAttribute("value");
            String target = value.trim().toLowerCase();
            return (selectedText != null && selectedText.trim().toLowerCase().contains(target))
                || (selectedValue != null && selectedValue.trim().toLowerCase().contains(target));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean trySelectViaJavascript(RemoteWebDriver driver, WebElement element, String value) {
        try {
            Object result = ((JavascriptExecutor) driver).executeScript(
                "var select = arguments[0]; var target = arguments[1].trim().toLowerCase(); " +
                    "for (var i = 0; i < select.options.length; i++) { " +
                    "var option = select.options[i]; " +
                    "var text = (option.textContent || '').trim().toLowerCase(); " +
                    "var optionValue = (option.value || '').trim().toLowerCase(); " +
                    "if (text === target || optionValue === target || text.indexOf(target) !== -1 || optionValue.indexOf(target) !== -1) { " +
                    "select.value = option.value; " +
                    "select.dispatchEvent(new Event('change', { bubbles: true })); " +
                    "return true; " +
                    "} " +
                    "} " +
                    "return false;",
                element,
                value
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    private WebElement findElementWithFallback(RemoteWebDriver driver, FieldMapping mapping, String sessionId) {
        try {
            return driver.findElement(mapping.toBy());
        } catch (Exception first) {
            String locatorValue = mapping.locatorValue();
            String fallback = locatorValue;
            if (locatorValue != null && !locatorValue.isBlank()) {
                fallback = Character.toLowerCase(locatorValue.charAt(0)) + locatorValue.substring(1);
            }
            try {
                return driver.findElement(By.cssSelector("[name='" + locatorValue + "']"));
            } catch (Exception ignored) {
                try {
                    return driver.findElement(By.cssSelector("[name='" + fallback + "']"));
                } catch (Exception ignored2) {
                    log.debug("session={} no element found for locator {}:{}", sessionId, mapping.locatorType(), mapping.locatorValue());
                    return null;
                }
            }
        }
    }

    private void grantGeolocationPermission(RemoteWebDriver driver, String origin, String sessionId) {
        if (origin == null || origin.isBlank()) {
            return;
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("origin", origin);
            params.put("permission", Map.of("name", "geolocation"));
            params.put("setting", "granted");
            Method method = driver.getClass().getMethod("executeCdpCommand", String.class, Map.class);
            method.invoke(driver, "Browser.setPermission", params);
            log.info("session={} granted geolocation permission for {}", sessionId, origin);
        } catch (Exception e) {
            log.debug("session={} could not pre-grant geolocation permission for {}: {}", sessionId, origin, e.getMessage());
        }
    }

    private void waitForLoginPage(RemoteWebDriver driver, String targetUrl, String sessionId) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(15));
            if (targetUrl.contains("Campus360") || targetUrl.contains("hmtcampus360v2.net")) {
                wait.until(ExpectedConditions.presenceOfElementLocated(By.id("StudentEmail")));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.id("StudentPassword")));
                log.info("session={} detected Campus360 login page", sessionId);
            } else {
                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("form")));
                log.info("session={} detected login form page", sessionId);
            }
        } catch (Exception e) {
            log.warn("session={} did not detect login page at targetUrl='{}': {}", sessionId, targetUrl, e.getMessage());
        }
    }

    private void autoSubmitIfNeeded(RemoteWebDriver driver, String targetUrl, String sessionId) {
        try {
            if (targetUrl.contains("Campus360") || targetUrl.contains("hmtcampus360v2.net")) {
                WebElement loginButton = driver.findElement(By.id("StudentLogin"));
                loginButton.click();
                log.info("session={} clicked Campus360 login button", sessionId);
                return;
            }

            WebElement form = driver.findElement(By.tagName("form"));
            form.submit();
            log.info("session={} submitted generic form", sessionId);
        } catch (Exception e) {
            log.warn("session={} could not auto-submit login form: {}", sessionId, e.getMessage());
        }
    }

    String resolvePostLoginUrl(String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) {
            return targetUrl;
        }

        String trimmed = targetUrl.trim();
        if (trimmed.contains("hmtcampus360v2.net")) {
            return "https://hmtcampus360v2.net/AdminPanel/Admission_new/personal_profile.php";
        }
        return trimmed;
    }

    private void waitForPostLoginNavigation(RemoteWebDriver driver, String sessionId) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(20));
            wait.until(webDriver -> {
                String currentUrl = webDriver.getCurrentUrl();
                return currentUrl != null && (currentUrl.contains("personal_profile.php") || currentUrl.contains("Dashboard") || currentUrl.contains("dashboard"));
            });
            log.info("session={} post-login navigation reached {}", sessionId, driver.getCurrentUrl());
        } catch (Exception e) {
            log.warn("session={} did not reach a post-login page yet: {}", sessionId, e.getMessage());
        }
    }

    private void dismissBlockingModal(RemoteWebDriver driver, String sessionId) {
        try {
            JavascriptExecutor js = driver;
            js.executeScript("document.querySelectorAll('#feedbackModal, .modal').forEach(el => el.remove());");
            log.debug("session={} dismissed blocking modal overlays", sessionId);
        } catch (Exception e) {
            log.debug("session={} no blocking modal to dismiss: {}", sessionId, e.getMessage());
        }
    }

    private void navigateToPersonalProfileFromDashboard(RemoteWebDriver driver, String sessionId) {
        try {
            List<WebElement> hrefLinks = driver.findElements(By.cssSelector("a[href*='personal_profile.php']"));
            if (!hrefLinks.isEmpty()) {
                hrefLinks.get(0).click();
                log.info("session={} clicked direct personal profile link", sessionId);
                return;
            }

            List<WebElement> links = driver.findElements(By.tagName("a"));
            for (WebElement link : links) {
                String text = link.getText();
                if (text != null && (text.contains("Create / Update") || text.contains("Create/Update") || text.contains("Create") && text.contains("Update"))) {
                    link.click();
                    log.info("session={} clicked dashboard admissions link", sessionId);
                    return;
                }
            }

            WebElement profileLink = driver.findElement(By.partialLinkText("Create / Update"));
            profileLink.click();
            log.info("session={} clicked dashboard admissions link via partial link", sessionId);
        } catch (Exception e) {
            log.warn("session={} could not navigate from dashboard to personal profile: {}", sessionId, e.getMessage());
        }
    }

    private void waitForPersonalProfilePage(RemoteWebDriver driver, String sessionId) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(25));
            wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(By.id("FirstName")),
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[name='FirstName']")),
                ExpectedConditions.presenceOfElementLocated(By.xpath("//h5[contains(., 'PERSONAL INFORMATION')]"))
            ));
            log.info("session={} loaded personal profile form", sessionId);
        } catch (Exception e) {
            log.warn("session={} did not detect personal profile form page: {}", sessionId, e.getMessage());
        }
    }

    private void waitForFormControlsToBeInteractive(RemoteWebDriver driver, String sessionId) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(20));
            wait.until(ExpectedConditions.and(
                ExpectedConditions.elementToBeClickable(By.id("FirstName")),
                ExpectedConditions.elementToBeClickable(By.id("Religion")),
                ExpectedConditions.presenceOfElementLocated(By.id("Gender"))
            ));
            log.info("session={} form controls are interactive", sessionId);
        } catch (Exception e) {
            log.debug("session={} form controls were not fully interactive yet: {}", sessionId, e.getMessage());
        }
    }

    /** Called after the human has visually verified the filled form. */
    public void submit(FlowSession session, String submitLocatorType, String submitLocatorValue) {
        FieldMapping submit = new FieldMapping("submit", submitLocatorType, submitLocatorValue);
        session.getDriver().findElement(submit.toBy()).click();
        session.setStatus(FlowSession.Status.SUBMITTED);
    }

    /** Human cancelled: discard the session and quit the browser. */
    public void cancel(FlowSession session) {
        session.setStatus(FlowSession.Status.CLOSED);
        registry.remove(session.getId());
    }

    private boolean isLocal() {
        return "local".equalsIgnoreCase(mode);
    }

    private RemoteWebDriver createDriver() throws Exception {
        ChromeOptions options = new ChromeOptions();
        if (isLocal()) {
            // Real, visible Chrome on this machine. No Docker, no Selenoid.
            // Selenium Manager (built into Selenium 4.6+) downloads chromedriver automatically.
            options.addArguments("--start-maximized");
            options.addArguments("--remote-allow-origins=*");
            options.setExperimentalOption("prefs", Map.of(
                "profile.default_content_setting_values.geolocation", 1,
                "profile.content_settings.exceptions.geolocation.*.setting", 1
            ));
            log.info("Starting LOCAL Chrome browser (visible window on this machine)");
            return new ChromeDriver(options);
        }

        // Remote (Selenoid/Grid) mode
        if (enableVnc) {
            Map<String, Object> selenoidOptions = new HashMap<>();
            selenoidOptions.put("enableVNC", true);
            selenoidOptions.put("enableVideo", false);
            selenoidOptions.put("sessionTimeout", "10m");
            options.setCapability("selenoid:options", selenoidOptions);
        }
        URL url = java.net.URI.create(seleniumUrl).toURL();
        return new RemoteWebDriver(url, options);
    }

    String extractOrigin(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    /** Local mode has no VNC url; the browser is visible directly on the machine. */
    private String buildVncUrl(RemoteWebDriver driver) {
        if (isLocal()) {
            return null;
        }
        String seleniumSessionId = driver.getSessionId().toString();
        return selenoidUiBase + "/#/sessions/" + seleniumSessionId;
    }
}
