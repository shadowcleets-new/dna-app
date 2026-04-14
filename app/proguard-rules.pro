# Keep Kotlin metadata for reflection-based libraries.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# kotlinx.serialization
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}

# Hilt / Dagger — handled by the plugin, but keep generated classes safe.
-keep class dagger.hilt.** { *; }

# Coil
-dontwarn okhttp3.**
