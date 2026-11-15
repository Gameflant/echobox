plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.feringsapp.echobox.designsystem"
    compileSdk = 37
    compileSdkMinor = 0
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 34
    }

    buildFeatures {
        compose = true
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

    val bom = platform(libs.compose.bom)
    api(bom)
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.compose.runtime)
    debugApi(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    api(libs.miuix.ui)
    api(libs.miuix.icons)
    api(libs.miuix.blur)
    api(libs.miuix.material)
    api(libs.miuix.glass)
}
