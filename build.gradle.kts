plugins {
    java
    id("xyz.wagyourtail.unimined") version "1.4.2-SNAPSHOT"
}

val node = stonecutter.current.project
val minecraftVersion = stonecutter.current.version
val loader = node.substringAfterLast('-')
val legacy = minecraftVersion.startsWith("1.7.") || minecraftVersion.startsWith("1.12.")
val javaVersion = when {
    minecraftVersion.startsWith("26.") -> 25
    minecraftVersion.startsWith("1.21") -> 21
    else -> 17
}

group = "sensusmmc"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(if (legacy) 17 else javaVersion)
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.neoforged.net/releases")
    maven("https://maven.minecraftforge.net/")
}

val modernSources = when {
    node.startsWith("1.20.1") -> "src/modern1201/java"
    node.startsWith("1.21.1") -> "src/modern1211/java"
    node.startsWith("26.2") -> "src/modern262/java"
    else -> null
}
if (modernSources != null) {
    sourceSets.main {
        java.srcDir(rootProject.file(modernSources))
    }
}

val platformSources = when (node) {
    "1.7.10-forge" -> "src/forge1710/java"
    "1.12.2-forge" -> "src/forge1122/java"
    "1.20.1-forge" -> "src/forge1201/java"
    "1.20.1-fabric" -> "src/fabric1201/java"
    "1.21.1-neoforge" -> "src/neoforge1211/java"
    "1.21.1-fabric" -> "src/fabric1211/java"
    "26.2-neoforge" -> "src/neoforge262/java"
    "26.2-fabric" -> "src/fabric262/java"
    else -> null
}
if (platformSources != null) {
    sourceSets.main {
        java.srcDir(rootProject.file(platformSources))
    }
}

if (node == "1.20.1-forge") {
    sourceSets.main {
        resources.srcDir(rootProject.file("src/forge1201/resources"))
    }
    tasks.configureEach {
        if (this is JavaExec && name == "runClient") {
            dependsOn(tasks.named("jar"))
            doFirst {
                val devJar = layout.buildDirectory.file("libs/${project.name}-${project.version}-dev.jar").get().asFile
                val mods = layout.projectDirectory.dir("run/client/mods").asFile
                mods.mkdirs()
                devJar.copyTo(mods.resolve("sensusmmc.jar"), overwrite = true)
            }
        }
    }
}

if (node == "1.20.1-fabric") {
    sourceSets.main {
        resources.srcDir(rootProject.file("src/fabric1201/resources"))
    }
}

if (node == "1.12.2-forge") {
    sourceSets.main {
        resources.srcDir(rootProject.file("src/forge1122/resources"))
    }
}

if (node == "1.7.10-forge") {
    sourceSets.main {
        resources.srcDir(rootProject.file("src/forge1710/resources"))
    }
}

if (node == "1.21.1-fabric") {
    sourceSets.main {
        resources.srcDir(rootProject.file("src/fabric1211/resources"))
    }
}

if (node == "1.21.1-neoforge") {
    sourceSets.main {
        resources.srcDir(rootProject.file("src/neoforge1211/resources"))
    }
}

if (node == "26.2-neoforge") {
    sourceSets.main {
        resources.srcDir(rootProject.file("src/neoforge262/resources"))
        resources.srcDir(rootProject.file("src/modern262/resources"))
    }
}

if (node == "26.2-fabric") {
    sourceSets.main {
        resources.srcDir(rootProject.file("src/fabric262/resources"))
        resources.srcDir(rootProject.file("src/modern262/resources"))
    }
}

unimined.minecraft {
    version = minecraftVersion

    when (loader) {
        "forge" -> minecraftForge {
            loader(
                when (node) {
                    "1.7.10-forge" -> "10.13.4.1614-1.7.10"
                    "1.12.2-forge" -> "14.23.5.2864"
                    "1.20.1-forge" -> "47.4.28"
                    else -> error("No Forge version for $node")
                }
            )
        }
        "fabric" -> fabric {
            loader(
                when (node) {
                    "1.20.1-fabric", "1.21.1-fabric" -> "0.16.14"
                    "26.2-fabric" -> "0.19.5"
                    else -> error("No Fabric loader version for $node")
                }
            )
            if (node == "1.20.1-fabric") {
                accessWidener(rootProject.file("src/fabric1201/resources/sensusmmc.accesswidener"))
            }
            if (node == "1.21.1-fabric") {
                accessWidener(rootProject.file("src/fabric1211/resources/sensusmmc.accesswidener"))
            }
        }
        "neoforge" -> neoForge {
            loader(
                when (node) {
                    "1.21.1-neoforge" -> "264"
                    "26.2-neoforge" -> "93"
                    else -> error("No NeoForge version for $node")
                }
            )
        }
        else -> error("Unknown loader in $node")
    }

    mappings {
        if (legacy) {
            searge()
            mcp("stable", if (minecraftVersion.startsWith("1.7.")) "12-1.7.10" else "39-1.12")
        } else {
            mojmap()
        }
    }
}

if (node == "1.20.1-fabric") {
    dependencies {
        add("modImplementation", "net.fabricmc.fabric-api:fabric-api:0.92.12+1.20.1")
    }
}

if (node == "1.21.1-fabric") {
    dependencies {
        add("modImplementation", "net.fabricmc.fabric-api:fabric-api:0.116.17+1.21.1")
    }
}

if (node == "26.2-fabric") {
    dependencies {
        add("modImplementation", "net.fabricmc.fabric-api:fabric-api:0.161.0+26.2")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(if (legacy) 8 else javaVersion)
}

tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
}

tasks.register<JavaExec>("libraryTests") {
    group = "verification"
    description = "Runs the dependency-free library checks."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("sensusmmc.test.LibraryTests")
    workingDir = rootProject.projectDir
}
