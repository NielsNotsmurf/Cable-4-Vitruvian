# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Room classes
-keep class com.example.vitruvianredux.data.local.** { *; }
-keep class com.example.vitruvianredux.data.local.entity.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase {
    public static <methods>;
}
-keep @androidx.room.Entity class *

# Keep Hilt/Dagger
-keep class com.example.vitruvianredux.di.** { *; }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep BLE classes
-keep class com.example.vitruvianredux.data.ble.** { *; }
-keep class no.nordicsemi.android.ble.** { *; }

# Keep data models used in reflection or serialization
-keep class com.example.vitruvianredux.domain.model.** { *; }

# Keep all app packages used by Hilt, DataStore, and kotlinx.serialization
-keep class com.example.vitruvianredux.data.** { *; }
-keep class com.example.vitruvianredux.util.** { *; }

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.vitruvianredux.**$$serializer { *; }
-keepclassmembers class com.example.vitruvianredux.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.vitruvianredux.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler {
    <init>();
}

# Garmin Connect IQ companion SDK: binds to Garmin Connect Mobile over AIDL and parcels its own types
-keep class com.garmin.** { *; }
-dontwarn com.garmin.**
