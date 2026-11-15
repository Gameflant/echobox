# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.feringsapp.echobox.** {
    *** Companion;
}
-keepclasseswithmembers class com.feringsapp.echobox.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.feringsapp.echobox.**$$serializer { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Media3
-dontwarn androidx.media3.**

# ffmpeg-kit
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn kotlinx.atomicfu.**
