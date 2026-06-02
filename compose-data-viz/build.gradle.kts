@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain {
        languageVersion.set(
            JavaLanguageVersion.of(
                libs.versions.languageVersion
                    .get()
                    .toInt(),
            ),
        )
    }
    android {
        namespace = libs.versions.libraryNamespace.get()
        compileSdk =
            libs.versions.androidSdk
                .get()
                .toInt()
    }
    applyDefaultHierarchyTemplate()

    jvm()

    iosArm64()
    iosSimulatorArm64()

    macosArm64()

    js {
        browser()
        nodejs()
    }

    wasmJs {
        browser()
        nodejs()
        d8()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.foundation)
            api(projects.composeDataVizCore)
            implementation(libs.immutablelist)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        libs.versions.libraryGroup.get(),
        libs.versions.dataVizArtifact.get(),
        libs.versions.composedataviz.get(),
    )
}
