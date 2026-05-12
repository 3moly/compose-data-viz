@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.serialization)
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    android {
        namespace = "io.github.moly3.composedatavizcore"
        compileSdk = 36
//        defaultConfig {
//            minSdk = 21
//        }
//        compileOptions {
//            sourceCompatibility = JavaVersion.VERSION_17
//            targetCompatibility = JavaVersion.VERSION_17
//        }
    }
    applyDefaultHierarchyTemplate()

//    androidTarget()
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
            implementation(libs.serialization)
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
        "compose-data-viz-core",
        libs.versions.composedatavizcore.get()
    )
}