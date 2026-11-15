pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp") {
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://repo1.maven.org/maven2") }
    }
}

includeBuild("/workspace/miuix") {
    dependencySubstitution {
        substitute(module("top.yukonga.miuix.kmp:miuix-ui")).using(project(":miuix-ui"))
        substitute(module("top.yukonga.miuix.kmp:miuix-ui-android")).using(project(":miuix-ui"))
        substitute(module("top.yukonga.miuix.kmp:miuix-blur")).using(project(":miuix-blur"))
        substitute(module("top.yukonga.miuix.kmp:miuix-blur-android")).using(project(":miuix-blur"))
        substitute(module("top.yukonga.miuix.kmp:miuix-icons")).using(project(":miuix-icons"))
        substitute(module("top.yukonga.miuix.kmp:miuix-icons-android")).using(project(":miuix-icons"))
        substitute(module("top.yukonga.miuix.kmp:miuix-preference")).using(project(":miuix-preference"))
        substitute(module("top.yukonga.miuix.kmp:miuix-preference-android")).using(project(":miuix-preference"))
        substitute(module("top.yukonga.miuix.kmp:miuix-material")).using(project(":miuix-material"))
        substitute(module("top.yukonga.miuix.kmp:miuix-material-android")).using(project(":miuix-material"))
        substitute(module("top.yukonga.miuix.kmp:miuix-glass")).using(project(":miuix-glass"))
        substitute(module("top.yukonga.miuix.kmp:miuix-glass-android")).using(project(":miuix-glass"))
        substitute(module("top.yukonga.miuix.kmp:miuix-glass-icons")).using(project(":miuix-glass-icons"))
        substitute(module("top.yukonga.miuix.kmp:miuix-glass-icons-android")).using(project(":miuix-glass-icons"))
        substitute(module("top.yukonga.miuix.kmp:miuix-core")).using(project(":miuix-core"))
        substitute(module("top.yukonga.miuix.kmp:miuix-core-android")).using(project(":miuix-core"))
        substitute(module("top.yukonga.miuix.kmp:miuix-squircle")).using(project(":miuix-squircle"))
        substitute(module("top.yukonga.miuix.kmp:miuix-squircle-android")).using(project(":miuix-squircle"))
    }
}

rootProject.name = "EchoBox"

include(":app")
include(":core")
include(":core:media")
include(":core:designsystem")
include(":feature:main")
include(":feature:player")
include(":feature:upload")
