plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
}

group = "alexeyyuditsky"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "alexeyyuditsky.MainKt"
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(libs.logback.classic)

    implementation(ktorLibs.client.core)
    implementation(ktorLibs.client.cio)
    implementation(libs.playwright)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}

tasks.register<JavaExec>("playwrightInstall") {
    group = "playwright"
    description = "Installs Chromium used by Playwright"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.microsoft.playwright.CLI"
    args("install", "chromium")
}