// GRADLE PLUGINS
plugins {
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
    // kotlin("jvm") version "1.9.24" // if using Kotlin
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

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
