import com.plakagozcusu.buildlogic.PROJECT_JAVA_VERSION
import com.plakagozcusu.buildlogic.configureKotlinCompiler
import com.plakagozcusu.buildlogic.configureStaticAnalysis
import com.plakagozcusu.buildlogic.lib
import com.plakagozcusu.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Pure Kotlin/JVM module: no Android dependency, fastest tests (domain, vision logic). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        // Lets Android Lint analyze pure-Kotlin modules too (they are consumed by Android modules).
        pluginManager.apply("com.android.lint")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = PROJECT_JAVA_VERSION
            targetCompatibility = PROJECT_JAVA_VERSION
        }
        configureKotlinCompiler()
        configureStaticAnalysis()
        dependencies {
            add("testImplementation", libs.lib("junit4"))
            add("testImplementation", libs.lib("truth"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
            add("testImplementation", libs.lib("turbine"))
        }
    }
}
