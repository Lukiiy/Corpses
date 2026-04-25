plugins {
    java
    id("com.gradleup.shadow") version "9.4.1"
}

group = "me.lukiiy"
version = "1.0-SNAPSHOT"
description = "Displays corpses of those who perished."

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.9-R0.1-SNAPSHOT")
    implementation(files("lib/ManneInventory-1.1.jar"))
}

tasks {
    shadowJar {
        mergeServiceFiles()
        archiveClassifier.set("")
        minimize()
    }

    build { dependsOn(shadowJar) }

    processResources {
        val props = mapOf("version" to version, "desc" to rootProject.description)

        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") { expand(props) }
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    withSourcesJar()
}