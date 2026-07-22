// GRADLE PLUGINS
plugins {
    java
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.7"
}

// PROJECT LANGUAGE
java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

// DEPENDENCY MANAGEMENT
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
    }
}

// PROJECT DEPENDENCIES
dependencies {
    //////////////////////////////////
    // Production code dependencies //
    //////////////////////////////////
    // Web + Thymeleaf UI
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    // Security (JWT is built on top of this)
    implementation("org.springframework.boot:spring-boot-starter-security")
    // Caching abstraction (used for the session registry/mapping cache)
    implementation("org.springframework.boot:spring-boot-starter-cache")
    // Validation for request DTOs
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Selenium (RemoteWebDriver talks to Selenoid, or local ChromeDriver)
    implementation("org.seleniumhq.selenium:selenium-java:${property("seleniumVersion")}")
    // CSV parsing for mapping.csv / data.csv
    implementation("org.apache.commons:commons-csv:${property("commonsCsvVersion")}")
    // JWT (JJWT)
    implementation("io.jsonwebtoken:jjwt-api:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:${property("jjwtVersion")}")

    ////////////////////////////
    // Test code dependencies //
    ////////////////////////////
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// TEST CONFIGURATION
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
