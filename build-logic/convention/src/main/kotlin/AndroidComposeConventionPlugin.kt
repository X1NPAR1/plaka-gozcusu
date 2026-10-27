import com.android.build.api.dsl.CommonExtension
import com.plakagozcusu.buildlogic.lib
import com.plakagozcusu.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Apply after an Android application/library convention plugin. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.getByType(CommonExtension::class.java).buildFeatures.compose = true
        dependencies {
            val bom = platform(libs.lib("compose-bom"))
            add("implementation", bom)
            add("androidTestImplementation", bom)
            add("implementation", libs.lib("compose-ui"))
            add("implementation", libs.lib("compose-foundation"))
            add("implementation", libs.lib("compose-material3"))
            add("implementation", libs.lib("compose-ui-tooling-preview"))
            add("debugImplementation", libs.lib("compose-ui-tooling"))
            add("testImplementation", libs.lib("compose-ui-test-junit4"))
            add("debugImplementation", libs.lib("compose-ui-test-manifest"))
        }
    }
}
