import com.android.build.api.dsl.ApplicationExtension
import com.plakagozcusu.buildlogic.configureAndroidCommon
import com.plakagozcusu.buildlogic.configureStaticAnalysis
import com.plakagozcusu.buildlogic.libs
import com.plakagozcusu.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)
            defaultConfig.targetSdk = libs.version("targetSdk").toInt()
        }
        configureStaticAnalysis()
    }
}
