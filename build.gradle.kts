plugins {
    java
}

group = "me.lukiiy"
version = "1.0-SNAPSHOT"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.9-R0.1-SNAPSHOT")
    implementation(files("lib/ManneInventory-1.0-SNAPSHOT.jar"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    withSourcesJar()
}