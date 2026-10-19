plugins {
    alias(libs.plugins.pricewatcher.kotlin.jvm)
}

dependencies {
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
}