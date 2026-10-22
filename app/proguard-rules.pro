# DownFlow ProGuard / R8 rules
# These are project-specific rules. AGP and library AARs supply their own
# consumer-rules; this file adds DownFlow-specific ones on top.

# ─── Kotlin ──────────────────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Kotlin coroutines (keep debug metadata)
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ─── Gson – keep all data classes used with TypeToken ────────────────────────
# JsonRpc models
-keep class com.downflow.app.aria2.JsonRpcRequest    { *; }
-keep class com.downflow.app.aria2.JsonRpcResponse   { *; }
-keep class com.downflow.app.aria2.JsonRpcError      { *; }
-keep class com.downflow.app.aria2.Aria2DownloadStatus { *; }
-keep class com.downflow.app.aria2.Aria2File         { *; }
-keep class com.downflow.app.aria2.Aria2Uri          { *; }
-keep class com.downflow.app.aria2.Aria2Bittorrent   { *; }
-keep class com.downflow.app.aria2.Aria2TorrentInfo  { *; }
-keep class com.downflow.app.aria2.Aria2GlobalStat   { *; }
-keep class com.downflow.app.aria2.Aria2Version      { *; }

# Generic Gson TypeToken usage needs this
-keepattributes Signature
-keepattributes EnclosingMethod

# ─── Room ─────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *         { *; }
-keep @androidx.room.Dao class *            { *; }
-keep @androidx.room.Database class *       { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** INSTANCE;
}

# ─── Hilt ─────────────────────────────────────────────────────────────────────
# Hilt generates code at compile time; R8 needs the generated classes.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# ─── OkHttp ──────────────────────────────────────────────────────────────────
# OkHttp ships its own consumer rules, but keep this as a safety net.
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ─── DataStore + Security-Crypto ─────────────────────────────────────────────
-keep class androidx.datastore.** { *; }
-keep class androidx.security.crypto.** { *; }

# ─── WebView ─────────────────────────────────────────────────────────────────
# Keep JavaScript interface classes (none currently, but future-proof)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ─── Suppress library warnings ───────────────────────────────────────────────
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# ─── General Android / Jetpack ───────────────────────────────────────────────
-keepclassmembers class ** implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep line numbers for crash reports
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
