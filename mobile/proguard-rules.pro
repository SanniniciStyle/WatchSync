# In-app watch setup over wireless debugging (libadb-android). The library loads its TLS
# provider and parts of BouncyCastle by name, and SPAKE2 goes through JNI: keep them whole.
-keep class io.github.muntashirakon.** { *; }
-keep class org.conscrypt.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn io.github.muntashirakon.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn com.android.org.conscrypt.**
-dontwarn org.apache.harmony.xnet.provider.jsse.**
-dontwarn javax.naming.**
