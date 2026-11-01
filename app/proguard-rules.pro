# Preserve all app classes, models, ViewModels, and services from R8 stripping
-keep class com.minimal.pissh.** { *; }
-keepclassmembers class com.minimal.pissh.** { *; }

# Keep ViewModel constructors for reflection by ViewModelProvider
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Preserve JSch crypto algorithms, key exchange, and reflection classes
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# BouncyCastle and optional providers
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn com.sun.jna.**
-dontwarn org.newsclub.net.unix.**
-dontwarn javax.security.auth.**
