package com.example.seleniumdemo.service;

import com.example.seleniumdemo.model.FieldMapping;
import com.example.seleniumdemo.model.FlowSession;
import org.openqa.selenium.By;
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

    public SeleniumFlowService(MappingService mappingService,
                               SessionRegistry registry,
                               @Value("${app.selenium.mode:local}") String mode,
                               @Value("${app.selenium.remote-url:}") String seleniumUrl,
                               @Value("${app.selenium.selenoid-ui-base:}") String selenoidUiBase,
                               @Value("${app.selenium.enable-vnc:false}") boolean enableVnc) {
        this.mappingService = mappingService;
        this.registry = registry;
        this.mode = mode;
        this.seleniumUrl = seleniumUrl;
        this.selenoidUiBase = selenoidUiBase;
        this.enableVnc = enableVnc;
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
            driver.get(targetUrl);

            fillFields(driver, mappings, data, sessionId);

            if (targetUrl.contains("/sample-form-login")) {
                waitForSampleFormPage(driver, sessionId);
                fillFields(driver, mappings, data, sessionId);
            }

            session.setStatus(FlowSession.Status.AWAITING_SUBMIT);
        } catch (Exception e) {
            // Fatal error during driver creation/navigation: clean up and rethrow.
            session.setStatus(FlowSession.Status.ERROR);
            registry.remove(sessionId);
            throw e;
        }
        return session;
    }

    private void fillFields(RemoteWebDriver driver, List<FieldMapping> mappings, Map<String, String> data, String sessionId) {
        for (FieldMapping mapping : mappings) {
            String value = data.get(mapping.csvColumn());
            if (value == null || value.isEmpty()) {
                log.debug("No data for column '{}', skipping", mapping.csvColumn());
                continue;
            }
            try {
                WebElement element = driver.findElement(mapping.toBy());
                String tag = element.getTagName();
                String type = element.getAttribute("type");

                if ("select".equalsIgnoreCase(tag)) {
                    try {
                        Select sel = new Select(element);
                        try {
                            sel.selectByVisibleText(value);
                        } catch (Exception ex) {
                            sel.selectByValue(value);
                        }
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

    private void waitForSampleFormPage(RemoteWebDriver driver, String sessionId) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(10));
            wait.until(webDriver -> webDriver.getCurrentUrl().contains("/sample-form"));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("first-name")));
            log.info("session={} detected /sample-form and first-name field is present", sessionId);
        } catch (Exception e) {
            log.warn("session={} did not fully load /sample-form after login auto-submit: {}", sessionId, e.getMessage());
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

    /** Local mode has no VNC url; the browser is visible directly on the machine. */
    private String buildVncUrl(RemoteWebDriver driver) {
        if (isLocal()) {
            return null;
        }
        String seleniumSessionId = driver.getSessionId().toString();
        return selenoidUiBase + "/#/sessions/" + seleniumSessionId;
    }
}
