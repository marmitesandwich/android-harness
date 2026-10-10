# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class dev.harness.**$$serializer { *; }
-keepclassmembers class dev.harness.** {
    *** Companion;
}
-keepclasseswithmembers class dev.harness.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Shizuku
-keep class rikka.shizuku.** { *; }
