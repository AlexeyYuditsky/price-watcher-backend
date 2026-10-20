plugins {
    alias(libs.plugins.pricewatcher.kotlin.jvm)
}

dependencies {
    implementation(projects.core.database)
    implementation(projects.core.products)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)
}