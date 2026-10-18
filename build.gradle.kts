plugins {
    alias(libs.plugins.pricewatcher.kotlin.jvm) apply false
    alias(ktorLibs.plugins.ktor) apply false
    base
}

// Keep the existing commands and deployment output paths at the project root.
listOf("run", "installDist", "distZip", "distTar", "playwrightInstall").forEach { taskName ->
    tasks.register(taskName) {
        group = if (taskName == "run") "application" else "distribution"
        dependsOn(":app:$taskName")
    }
}

tasks.build {
    dependsOn(":app:build")
}

tasks.check {
    dependsOn(":app:check", ":core:browser:check", ":core:products:check", ":feature:ozon:check")
}

tasks.clean {
    dependsOn(":app:clean", ":core:browser:clean", ":core:products:clean", ":feature:ozon:clean")
}