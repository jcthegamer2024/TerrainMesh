plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.252-stable")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core:2.16.2")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit:2.16.2") { isTransitive = false }
    implementation("org.locationtech.jts:jts-core:1.20.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    runServer {
        minecraftVersion("26.3")
        downloadPlugins {
            github("IntellectualSites", "FastAsyncWorldEdit", "2.16.2", "FastAsyncWorldEdit-Paper-2.16.2.jar")
        }
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

tasks.test { useJUnitPlatform() }
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
}
