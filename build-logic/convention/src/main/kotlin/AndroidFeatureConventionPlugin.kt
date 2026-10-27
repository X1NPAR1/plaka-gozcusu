import com.plakagozcusu.buildlogic.lib
import com.plakagozcusu.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Feature module = Android library + Compose + Hilt + the core modules every screen needs. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("plakagozcusu.android.library")
        pluginManager.apply("plakagozcusu.android.compose")
        pluginManager.apply("plakagozcusu.hilt")
        dependencies {
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-hilt-navigation-compose"))
            add("implementation", libs.lib("androidx-navigation-compose"))
        }
    }
}
