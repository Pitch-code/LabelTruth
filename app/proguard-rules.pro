# ---- LabelLens R8 / ProGuard rules ----

# Keep line numbers so Play Console crash reports stay readable, but hide the
# original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- kotlinx.serialization ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.labellens.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.labellens.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.labellens.app.**$$serializer { *; }

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- ML Kit ----
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ---- Coroutines ----
-dontwarn kotlinx.coroutines.**
