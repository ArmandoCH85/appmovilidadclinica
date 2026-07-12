# ProGuard rules for Driver App

# Keep DataStore
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Hilt
-keep class * extends java.lang.annotation.Annotation { *; }

# Keep Retrofit
-keep class retrofit2.** { *; }
-keepclassmembers class retrofit2.** { *; }
