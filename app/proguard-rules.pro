# Preserve JSch crypto algorithms, key exchange, and reflection classes
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# Suppress warnings from optional providers; R8 keeps only the BouncyCastle classes referenced by com.jcraft.jsch.bc.*
-dontwarn org.bouncycastle.**
-dontwarn com.sun.jna.**
-dontwarn org.newsclub.net.unix.**
-dontwarn javax.security.auth.**
