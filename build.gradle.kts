plugins {
    alias(libs.plugins.pricewatcher.kotlin.jvm) apply false
    alias(ktorLibs.plugins.ktor) apply false
    base
}

// Keep the existing commands and deployment output paths at the project root.
listOf("run", "installDist", "distZip", "distTar", "playwrightInstall", "migrateDatabase").forEach { taskName ->
    tasks.register(taskName) {
        group = when (taskName) {
            "run" -> "application"
            "migrateDatabase" -> "database"
            else -> "distribution"
        }
        dependsOn(":app:$taskName")
    }
}

tasks.build {
    dependsOn(":app:build")
}

tasks.check {
    dependsOn(":app:check", ":core:browser:check", ":core:database:check", ":core:products:check", ":feature:ozon:check", ":feature:auth:check")
}

tasks.clean {
    dependsOn(":app:clean", ":core:browser:clean", ":core:database:clean", ":core:products:clean", ":feature:ozon:clean", ":feature:auth:clean")
}