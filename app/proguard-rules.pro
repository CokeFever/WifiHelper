# WiFi Helper ProGuard Rules

# ── Hilt / Dagger ──────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── Reflection-based Hotspot API (API 28-32) ───────────────────────────
# These hidden APIs are invoked via reflection in HotspotApiAdapterDirect
-keepclassmembers class android.net.ConnectivityManager {
    void startTethering(int, boolean, **, android.os.Handler);
    void stopTethering(int);
}
-keepclassmembers class android.net.ConnectivityManager$OnStartTetheringCallback {
    *;
}
-keepclassmembers class android.net.wifi.WifiManager {
    int getWifiApState();
}

# ── Kotlin Coroutines ──────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── App model classes (used with StateFlow) ────────────────────────────
-keep class app.ixo.wifihelper.model.** { *; }

# ── WorkManager + Hilt Worker ──────────────────────────────────────────
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# ── Remove Log calls in release (security: CWE-532) ───────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
