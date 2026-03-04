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
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    android {
        namespace = "io.github.moly3.composedataviz"
        compileSdk = 36
//    defaultConfig {
//        minSdk = 21
//    }
//    compileOptions {
//        sourceCompatibility = JavaVersion.VERSION_17
//        targetCompatibility = JavaVersion.VERSION_17
//    }
    }
    applyDefaultHierarchyTemplate()

//    androidTarget()
    jvm()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    macosX64()
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
        "io.github.3moly",
        "compose-data-viz",
        libs.versions.composedataviz.get()
    )
}