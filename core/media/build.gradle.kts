plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.feringsapp.echobox.media"
    compileSdk = 37
    compileSdkMinor = 0
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
}

dependencies {
    implementation(project(":core"))
    api(libs.media3.exoplayer)
    api(libs.media3.session)
    implementation(libs.media3.datasource)
    implementation(libs.media3.database)
    api(libs.kotlinx.serialization.json)
}
