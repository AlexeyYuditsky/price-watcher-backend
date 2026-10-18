plugins {
    alias(libs.plugins.pricewatcher.kotlin.jvm)
}

dependencies {
    implementation(libs.playwright)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)
}