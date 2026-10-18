import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper

class KotlinJvmConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(KotlinPluginWrapper::class.java)
        group = "alexeyyuditsky"
        version = providers.gradleProperty("backendVersion").get()
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }
    }
}