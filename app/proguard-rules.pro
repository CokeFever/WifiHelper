# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep reflection-based Hotspot API calls (API 28-32)
-keepclassmembers class android.net.ConnectivityManager {
    void startTethering(...);
    void stopTethering(...);
}
