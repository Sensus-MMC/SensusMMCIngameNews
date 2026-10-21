pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
        maven("https://maven.wagyourtail.xyz/releases")
        maven("https://maven.wagyourtail.xyz/snapshots")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.minecraftforge.net/")
        maven("https://repo.spongepowered.org/repository/maven-public/")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.10-beta.1"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        version("1.20.1-forge", "1.20.1")
        version("1.20.1-fabric", "1.20.1")
        version("1.21.1-neoforge", "1.21.1")
        version("1.21.1-fabric", "1.21.1")
        vcsVersion = "1.20.1-forge"
    }
}

rootProject.name = "SensusMMCIngameNews"
