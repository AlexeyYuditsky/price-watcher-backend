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

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
