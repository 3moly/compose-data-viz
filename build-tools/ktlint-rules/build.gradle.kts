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
    implementation(libs.ktlint.rule.engine)
    implementation(libs.ktlint.ruleset.standard)
    testImplementation(libs.ktlint.test)
    testImplementation(libs.kotlin.test)
    runtimeOnly(libs.slf4j)
}
