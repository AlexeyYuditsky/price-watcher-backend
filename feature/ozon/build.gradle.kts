plugins {
    id("pricewatcher.kotlin-jvm")
}

dependencies {
    implementation(project(":core:browser"))
    implementation(project(":core:products"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
}