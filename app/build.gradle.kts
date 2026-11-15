import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.feringsapp.echobox"
    compileSdk = 37
    compileSdkMinor = 0
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.feringsapp.echobox"
        minSdk = 34
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
        ndk { abiFilters += listOf("arm64-v8a") }
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"${localProps.getProperty("github.clientId", "")}\"")
        buildConfigField("String", "STAGING_OWNER", "\"${localProps.getProperty("github.stagingOwner", "")}\"")
        buildConfigField("String", "STAGING_REPO", "\"${localProps.getProperty("github.stagingRepo", "")}\"")
        buildConfigField("String", "PROD_OWNER", "\"${localProps.getProperty("prod.owner", "echobox")}\"")
        buildConfigField("String", "PROD_REPO", "\"${localProps.getProperty("prod.repo", "echobox-music")}\"")
        buildConfigField("String", "PROD_BRANCH", "\"${localProps.getProperty("prod.branch", "main")}\"")
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.getProperty("storeFile") != null) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // .so 压缩存储：APK 从 24MB 降到 ~10MB；安装时解压，Android 14 上代价可以接受
        jniLibs { useLegacyPackaging = true }
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/DEPENDENCIES")
    }
}

kotlin {
    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":core:media"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:main"))
    implementation(project(":feature:player"))
    implementation(project(":feature:upload"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.splashscreen)
}
