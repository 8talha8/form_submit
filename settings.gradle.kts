// Plugin setup for the project.
pluginManagement {
    // Declare the MS Artifactory repository from which gradle plugins are pulled.
    repositories {
        maven {
            name = "pluginsBootstrap"
            url = uri("https://msdeartprod.ms.com/artifactory/train-maven-plugins-local")
        }
    }
    // Versioned plugins. Versions are centralized in gradle.properties.
    plugins {
        val pluginVersionMsdeBundle: String by settings
        id("com.ms.gradle.artifactory").version(pluginVersionMsdeBundle)
        val springBootPluginVersion: String by settings
        id("org.springframework.boot").version(springBootPluginVersion)
    }
}
