rootProject.name = "tu-helper"
include("backend", "frontend")

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://repo.spring.io/milestone") }
    }
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        "classpath"(group = "org.postgresql", name = "postgresql", version = "42.6.0")
    }
}
