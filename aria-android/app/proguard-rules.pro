# =============================================================================
# ARIA Android — ProGuard / R8 rules
# =============================================================================

# ---------------------------------------------------------------------------
# Moshi — JSON serialisation
# ---------------------------------------------------------------------------
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
# Retain generated JsonAdapter classes (KSP-generated names end in JsonAdapter).
-keep class **JsonAdapter { *; }
-keep class **JsonAdapter$* { *; }

# ---------------------------------------------------------------------------
# Room — database layer
# ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Dao class * { *; }

# ---------------------------------------------------------------------------
# Hilt — dependency injection
# ---------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep interface dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.Module class * { *; }
-keep @dagger.Provides class * { *; }
-keep @dagger.hilt.android.HiltWorker class * { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}

# ---------------------------------------------------------------------------
# ARIA data models and repository classes
# ---------------------------------------------------------------------------
-keep class com.aria.data.** { *; }
-keepclassmembers class com.aria.data.** { *; }

# PicoClaw config / integration layer
-keep class com.aria.picoclaw.** { *; }
-keepclassmembers class com.aria.picoclaw.** { *; }

# ---------------------------------------------------------------------------
# OkHttp — HTTP client (also used for SSE streaming to Claude)
# ---------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
# Required for OkHttp reflection on response body.
-keepclassmembers class okhttp3.internal.http.HttpMethod {
    public static boolean permitsRequestBody(java.lang.String);
}

# ---------------------------------------------------------------------------
# SnakeYAML — used when writing PicoClaw YAML config
# ---------------------------------------------------------------------------
-keep class org.yaml.snakeyaml.** { *; }
-keepclassmembers class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**

# ---------------------------------------------------------------------------
# Vosk — offline speech-to-text
# ---------------------------------------------------------------------------
-keep class org.vosk.** { *; }
-keepclassmembers class org.vosk.** { *; }
-dontwarn org.vosk.**
# Vosk uses JNI — keep the native method declarations.
-keepclasseswithmembernames class org.vosk.** {
    native <methods>;
}

# ---------------------------------------------------------------------------
# LiveKit — WebRTC voice calls
# ---------------------------------------------------------------------------
-keep class io.livekit.** { *; }
-keep interface io.livekit.** { *; }
-keepclassmembers class io.livekit.** { *; }
-dontwarn io.livekit.**
# WebRTC (bundled with LiveKit)
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keepclasseswithmembernames class org.webrtc.** {
    native <methods>;
}

# ---------------------------------------------------------------------------
# Kotlin Coroutines
# ---------------------------------------------------------------------------
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
# Keep coroutine debug metadata so crash stack traces are readable.
-keepattributes SourceFile,LineNumberTable

# ---------------------------------------------------------------------------
# WorkManager + Hilt Workers (SynthesisWorker)
# ---------------------------------------------------------------------------
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ---------------------------------------------------------------------------
# Kotlin metadata — required for reflection-based libraries (Moshi KotlinJsonAdapterFactory)
# ---------------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# ---------------------------------------------------------------------------
# Jetpack / AndroidX essentials
# ---------------------------------------------------------------------------
-keep class androidx.lifecycle.** { *; }
-keep interface androidx.lifecycle.** { *; }
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ---------------------------------------------------------------------------
# Sherpa-ONNX — offline TTS
# ---------------------------------------------------------------------------
-keep class com.k2fsa.sherpa.** { *; }
-keepclassmembers class com.k2fsa.sherpa.** { *; }
-dontwarn com.k2fsa.sherpa.**
-keepclasseswithmembernames class com.k2fsa.sherpa.** {
    native <methods>;
}

# ---------------------------------------------------------------------------
# Deepgram STT (cloud fallback) — plain OkHttp/JSON, no special rules needed
# beyond the OkHttp block above. Listed here for clarity.
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# General Android safety nets
# ---------------------------------------------------------------------------
# Keep Parcelable implementations (may be used in IPC / SavedState).
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable fields (used in some AndroidX internals).
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep custom Application, Activity, Service subclasses (referenced in manifest).
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Suppress warnings about missing optional dependencies.
-dontwarn javax.annotation.**
-dontwarn sun.misc.Unsafe
