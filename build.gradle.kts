import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    alias(libs.plugins.androidApplication).apply(false)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    alias(libs.plugins.compose).apply(false)
    alias(libs.plugins.maven.publish).apply(false)
    alias(libs.plugins.android.kotlin.multiplatform.library).apply(false)
    alias(libs.plugins.ktlint).apply(false)
    alias(libs.plugins.jetbrains.kotlin.jvm).apply(false)
}
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        debug.set(true)
        ignoreFailures.set(false)
        debug.set(true)
        verbose.set(true)
        android.set(false)
        outputToConsole.set(true)
        outputColorName.set("RED")
        enableExperimentalRules.set(true)
        filter {
            // Keep your standard string excludes for normal source files
            exclude("**/icons/**")
            exclude("**/sample/**")

            // ADD THIS: Force Gradle to check the absolute file path
            exclude { element ->
                val path = element.file.absolutePath
                // Checks for standard Mac/Linux (/) and Windows (\) file paths
                path.contains("/build/generated/") || path.contains("\\build\\generated\\")
            }
        }
    }

    dependencies {
        add("ktlint", project(":build-tools:ktlint-rules"))
    }
}

val installGitHook = tasks.register("installGitHook", Copy::class) {
    from("$rootDir/pre-commit")
    into("$rootDir/.git/hooks")

    filePermissions {
        unix("755")
    }
}

project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    val kmpExtension = project.extensions.getByType<KotlinMultiplatformExtension>()
    kmpExtension.targets.configureEach {
        compilations.configureEach {
            this.compileTaskProvider.dependsOn(installGitHook)
        }
    }
}