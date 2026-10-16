import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    id("pricewatcher.kotlin-jvm")
    alias(ktorLibs.plugins.ktor)
}

application {
    mainClass = "alexeyyuditsky.MainKt"
    applicationName = "price-watcher"
}

dependencies {
    implementation(project(":feature:ozon"))
    implementation(project(":core:browser"))
    implementation(project(":core:products"))
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.serialization.json)
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    providers.gradleProperty("backend").orNull?.let { environment("BACKEND_PROFILE", it) }
    providers.gradleProperty("port").orNull?.let { environment("PORT", it) }
}

tasks.named<Sync>("installDist") {
    into(rootProject.layout.buildDirectory.dir("install/price-watcher"))
}

tasks.withType<AbstractArchiveTask>().configureEach {
    if (name in setOf("distZip", "distTar")) {
        destinationDirectory = rootProject.layout.buildDirectory.dir("distributions")
        archiveBaseName = "price-watcher"
    }
}

tasks.register<JavaExec>("playwrightInstall") {
    group = "playwright"
    description = "Installs Chromium used by Playwright"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.microsoft.playwright.CLI"
    args("install", "chromium")
}