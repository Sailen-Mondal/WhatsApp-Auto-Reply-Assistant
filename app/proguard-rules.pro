# ─── WhatsApp Auto-Reply Assistant ProGuard Rules ────────────────────────────

# ─── General Kotlin / Android ─────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
-keepattributes InnerClasses,EnclosingMethod

# ─── Room Database ────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.TypeConverter class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
# Preserve all entity and DAO classes in the package
-keep class com.whatsappautoreply.data.database.** { *; }

# ─── Hilt / Dagger ────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep class **_Factory { *; }
-keep class **_HiltComponents { *; }
-keep class **_GeneratedInjector { *; }
-dontwarn dagger.hilt.**

# ─── Retrofit + OkHttp ────────────────────────────────────────────────────────
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keep interface retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep interface okhttp3.** { *; }
-dontwarn okio.**

# ─── Gson / JSON serialization ────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keep class com.whatsappautoreply.data.remote.llm.** { *; }
# Keep all data classes used as API request/response bodies
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ─── WorkManager ──────────────────────────────────────────────────────────────
-keep class androidx.work.** { *; }
-keep class com.whatsappautoreply.data.worker.** { *; }
-dontwarn androidx.work.**

# ─── Notification listener ────────────────────────────────────────────────────
-keep class com.whatsappautoreply.data.notification.** { *; }
-keep class com.whatsappautoreply.data.service.** { *; }

# ─── Domain layer (keep all enums for Room Converters) ────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep enum com.whatsappautoreply.** { *; }

# ─── DebugLogger (prevent stripping in debug builds) ─────────────────────────
-keep class com.whatsappautoreply.util.DebugLogger { *; }

# ─── Compose (must keep lambdas and internal infra) ──────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ─── Coroutines ───────────────────────────────────────────────────────────────
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
# Needed for coroutine debug agent
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ─── Kotlin Serialization ─────────────────────────────────────────────────────
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers class * {
    @kotlinx.serialization.* <methods>;
    @kotlinx.serialization.* <fields>;
}

# ─── Remove logs in release ───────────────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
