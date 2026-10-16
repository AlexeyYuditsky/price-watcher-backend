plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("kotlinJvmConventions") {
            id = "pricewatcher.kotlin-jvm"
            implementationClass = "KotlinJvmConventionPlugin"
        }
    }
}