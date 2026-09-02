pluginManagement {
    val kotlinVersion: String by settings
    val platformGradlePluginVersion: String by settings
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("jvm") version kotlinVersion
        id("org.jetbrains.intellij.platform") version platformGradlePluginVersion
    }
}

rootProject.name = "codex-ghost-text"
