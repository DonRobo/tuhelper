rootProject.name = "tu-helper"
include("backend", "frontend")

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://repo.spring.io/milestone") }
    }
}
