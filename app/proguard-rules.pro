# Standard Android & Portly R8 keep rules (no packing, string encryption, or anti-analysis obfuscation).
-keepattributes Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class com.portly.core.model.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }

# BouncyCastle & JCA security providers
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# SSHJ & EdDSA / JZlib
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }
-dontwarn net.schmizz.**
-dontwarn com.hierynomus.**

# Apache FtpServer & MINA
-keep class org.apache.ftpserver.** { *; }
-keep class org.apache.mina.** { *; }
-dontwarn org.apache.ftpserver.**
-dontwarn org.apache.mina.**

# jcifs-ng
-keep class jcifs.** { *; }
-dontwarn jcifs.**

# Ktor CIO & OkHttp
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Strip only verbose debug logs in release; retain Info/Warn/Error audit logging.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
