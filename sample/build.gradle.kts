@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import kotlin.collections.set

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    kotlin("native.cocoapods")
}

kotlin {
    applyDefaultHierarchyTemplate()

    jvm()
    androidTarget()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        version = "1.0"
        ios.deploymentTarget = "16.0"
        podfile = project.file("../iosApp/Podfile")

        framework {
            baseName = "shared"
            isStatic = true
        }
        xcodeConfigurationToNativeBuildType["debug"] = NativeBuildType.DEBUG
        xcodeConfigurationToNativeBuildType["release"] = NativeBuildType.RELEASE
        xcodeConfigurationToNativeBuildType["beta"] = NativeBuildType.DEBUG
        xcodeConfigurationToNativeBuildType["betarelease"] = NativeBuildType.RELEASE
    }

    macosX64()
    macosArm64()

    js {
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    //noinspection UseTomlInstead
    //keeping clean libs.versions.toml from libs that used only in this sample
    val coil = "3.3.0"
    sourceSets {
        commonMain.dependencies {
            implementation(project(":compose-data-viz"))
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)

            //noinspection UseTomlInstead
            implementation("com.mikepenz.hypnoticcanvas:hypnoticcanvas:0.4.1")
            //noinspection UseTomlInstead
            implementation("dev.chrisbanes.haze:haze:1.7.0")
            //noinspection UseTomlInstead
            implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")

            //noinspection UseTomlInstead
            implementation("io.coil-kt.coil3:coil:$coil")
            //noinspection UseTomlInstead
            implementation("io.coil-kt.coil3:coil-network-ktor3:$coil")
            //noinspection UseTomlInstead
            implementation("io.coil-kt.coil3:coil-compose:$coil")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            //noinspection UseTomlInstead
            implementation("androidx.activity:activity-compose:1.12.0")
            implementation("androidx.appcompat:appcompat:1.7.1")
            implementation("androidx.core:core-ktx:1.17.0")
            //noinspection UseTomlInstead
            implementation("io.ktor:ktor-client-okhttp:3.3.2")
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)

            //noinspection UseTomlInstead
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
            //noinspection UseTomlInstead
            implementation("io.ktor:ktor-client-java:3.3.2")
        }
    }
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    namespace = "com.threemoly.sample"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets["main"].apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.srcDirs("src/androidMain/resources")
        resources.srcDirs("src/commonMain/resources")
    }
}


compose.desktop {
    application {
        mainClass = "MainKt"
        // javaHome = System.getenv("JDK_17")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.company.app.desktopApp"
            packageVersion = "1.0.1"
        }
    }
}