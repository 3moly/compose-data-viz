plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {
    implementation("com.pinterest.ktlint:ktlint-rule-engine:1.8.0")
    implementation("com.pinterest.ktlint:ktlint-ruleset-standard:1.8.0")
    implementation("com.pinterest.ktlint:ktlint-test:1.8.0")
    implementation(libs.kotlin.test)
    implementation(libs.slf4j)
}