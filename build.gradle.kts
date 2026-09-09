import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

group = "com.leandro"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion").get())
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
}

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())
}

tasks.processResources {
    filesMatching("codex-ghost-text-version.properties") {
        expand("pluginVersion" to project.version)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("minimumBuild").get()
        }
    }
    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdea, providers.gradleProperty("verifierTarget2025_3").get())
            create(IntelliJPlatformType.IntellijIdea, providers.gradleProperty("verifierTarget2026_1").get())
            create(IntelliJPlatformType.IntellijIdea, providers.gradleProperty("verifierTarget2026_2").get())
        }
    }
}
