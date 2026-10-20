import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    alias(libs.plugins.pricewatcher.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
}

application {
    mainClass = "alexeyyuditsky.MainKt"
    applicationName = "price-watcher"
}

dependencies {
    implementation(projects.feature.auth)
    implementation(projects.feature.tracking)
    implementation(libs.kotlinx.coroutines.core)
    implementation(projects.feature.ozon)
    implementation(projects.core.database)
    implementation(projects.core.browser)
    implementation(projects.core.products)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.serialization.json)
}

tasks.run {
    workingDir = rootProject.projectDir
    providers.gradleProperty("backend").orNull?.let { environment("BACKEND_PROFILE", it) }
    providers.gradleProperty("port").orNull?.let { environment("PORT", it) }
}

tasks.installDist {
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
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.microsoft.playwright.CLI"
    args("install", "chromium")
}

tasks.processResources {
    from(rootProject.layout.projectDirectory.file("gradle.properties"))
}

tasks.register<JavaExec>("migrateDatabase") {
    group = "database"
    description = "Applies pending migrations to the configured PostgreSQL database"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "alexeyyuditsky.MainKt"
    args("--migrate")
    workingDir = rootProject.projectDir
    providers.gradleProperty("backend").orNull?.let { environment("BACKEND_PROFILE", it) }
}