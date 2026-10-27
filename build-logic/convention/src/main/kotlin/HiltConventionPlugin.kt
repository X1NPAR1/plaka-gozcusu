import com.plakagozcusu.buildlogic.lib
import com.plakagozcusu.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        dependencies {
            add("implementation", libs.lib("hilt-android"))
            add("ksp", libs.lib("hilt-compiler"))
            add("testImplementation", libs.lib("hilt-android-testing"))
            add("kspTest", libs.lib("hilt-compiler"))
            add("androidTestImplementation", libs.lib("hilt-android-testing"))
            add("kspAndroidTest", libs.lib("hilt-compiler"))
        }
    }
}
