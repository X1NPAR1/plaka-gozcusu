package com.plakagozcusu.buildlogic

import com.android.build.api.dsl.CommonExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

/** Java/Kotlin bytecode level for every module; 17 is supported by AGP 9 and all dependencies. */
internal val PROJECT_JAVA_VERSION = JavaVersion.VERSION_17
internal val PROJECT_JVM_TARGET = JvmTarget.JVM_17
private const val TEST_JDK = 21

/** Shared by ktlint (here) and detekt (config/detekt/detekt.yml). */
private const val MAX_LINE_LENGTH = "140"

internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.version("compileSdk").toInt()
        defaultConfig.minSdk = libs.version("minSdk").toInt()
        compileOptions.sourceCompatibility = PROJECT_JAVA_VERSION
        compileOptions.targetCompatibility = PROJECT_JAVA_VERSION
        testOptions.unitTests.isReturnDefaultValues = false
        testOptions.unitTests.isIncludeAndroidResources = true
        lint.warningsAsErrors = true
        lint.abortOnError = true
        lint.checkDependencies = true
        // Version-catalog upgrade nags are handled deliberately via MEMORY.md, not by lint.
        lint.disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
    configureKotlinCompiler()
    // Robolectric sandboxes for the current SDKs need a Java 21 runtime; bytecode stays at 17.
    val toolchains = extensions.getByType<JavaToolchainService>()
    tasks.withType<Test>().configureEach {
        javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(TEST_JDK)) })
        // Robolectric's FileDescriptor interceptor reflects into JDK internals.
        jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
    }
    dependencies {
        add("testImplementation", libs.lib("junit4"))
        add("testImplementation", libs.lib("truth"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("testImplementation", libs.lib("turbine"))
        add("testImplementation", libs.lib("robolectric"))
        add("testImplementation", libs.lib("androidx-test-core"))
        add("testImplementation", libs.lib("androidx-test-ext-junit"))
        add("androidTestImplementation", libs.lib("androidx-test-ext-junit"))
    }
}

internal fun Project.configureKotlinCompiler() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(PROJECT_JVM_TARGET)
            allWarningsAsErrors.set(true)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xconsistent-data-class-copy-visibility",
            )
        }
    }
}

/** Detekt + Spotless(ktlint) on every module so `./gradlew detekt spotlessCheck` covers the whole tree. */
internal fun Project.configureStaticAnalysis() {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    pluginManager.apply("com.diffplug.spotless")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
    }
    dependencies {
        add("detektPlugins", libs.lib("detekt-compose-rules"))
    }

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(libs.version("ktlint")).editorConfigOverride(
                mapOf(
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                    "max_line_length" to MAX_LINE_LENGTH,
                ),
            )
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(libs.version("ktlint"))
        }
    }
}
