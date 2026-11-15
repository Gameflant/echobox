plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.feringsapp.echobox.core"
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    api(libs.room.runtime)
    api(libs.room.ktx)
    ksp(libs.room.compiler)

    api(libs.ktor.client.core)
    api(libs.ktor.client.okhttp)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.serialization.json)
    implementation(libs.ktor.client.logging)

    api(libs.kotlinx.serialization.json)

    api(libs.coil.compose)
    implementation(libs.coil.network.ktor3)

    api(libs.datastore.preferences)
    api(libs.work.runtime.ktx)
    implementation(libs.security.crypto)

    api(libs.ffmpeg.kit.audio)
    // ffmpeg-kit-audio 的 aar 没带 pom，运行时需要它打短堆栈，否则 FFmpegKitConfig 初始化就崩
    implementation("com.arthenica:smart-exception-java:0.2.1")

    testImplementation(libs.junit)
}
