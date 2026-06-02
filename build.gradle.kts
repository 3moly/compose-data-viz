plugins {
    alias(libs.plugins.androidApplication).apply(false)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    alias(libs.plugins.compose).apply(false)
    alias(libs.plugins.maven.publish).apply(false)
    alias(libs.plugins.android.kotlin.multiplatform.library).apply(false)
    alias(libs.plugins.ktlint).apply(false)
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
}


subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        debug.set(true)
//        version.set("0.22.0")
        ignoreFailures.set(false)
        debug.set(true)
        verbose.set(true)
        android.set(false)
        outputToConsole.set(true)
        outputColorName.set("RED")
        ignoreFailures.set(true)
        enableExperimentalRules.set(true)
//        additionalEditorconfigFile.set(file("/some/additional/.editorconfig")) // not supported with ktlint 0.47+
//        additionalEditorconfig.set( // not supported until ktlint 0.49
//            mapOf(
//                "max_line_length" to "20"
//            )
//        )
    }
    dependencies {
        add("ktlint", project(":build-tools:ktlint-rules"))
    }
}