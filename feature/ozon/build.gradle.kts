plugins {
    alias(libs.plugins.pricewatcher.kotlin.jvm)
}

dependencies {
    implementation(projects.core.browser)
    implementation(projects.core.products)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
}