plugins {
    alias(libs.plugins.pricewatcher.kotlin.jvm)
}

dependencies {
    implementation(projects.core.database)
}