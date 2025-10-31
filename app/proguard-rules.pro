# ===============================
# Memely ProGuard Rules (Fixed)
# ===============================

# Add project-specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in ${sdk.dir}/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# For more details, see:
#   http://developer.android.com/guide/developing/tools/proguard.html

# ---------------------------------
# Debugging / Line Number Retention
# ---------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------
# Remove logging in release builds
# ---------------------------------
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

-assumenosideeffects class kotlin.io.ConsoleKt {
    public static *** println(...);
    public static *** print(...);
}

# ---------------------------------
# Keep Nostr cryptographic classes
# ---------------------------------
-keep class com.memely.nostr.** { *; }
-keep class fr.acinq.secp256k1.** { *; }

# ---------------------------------
# Keep data models
# ---------------------------------
-keep class com.memely.model.** { *; }

# ---------------------------------
# Keep Compose and AndroidX
# ---------------------------------
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ---------------------------------
# Keep Kotlin metadata
# ---------------------------------
-keep class kotlin.Metadata { *; }

# ---------------------------------
# Keep security crypto classes
# ---------------------------------
# (Add any crypto library classes here if needed)

# ---------------------------------
# Keep OkHttp & Coil
# ---------------------------------
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

-dontwarn coil.**
-keep class coil.** { *; }

# ---------------------------------
# Preserve annotations
# ---------------------------------
-keepattributes *Annotation*

# ---------------------------------
# Preserve native method names & JSON
# ---------------------------------
-keep class org.json.** { *; }

# keep BitcoinJ for Bech32
-keep class org.bitcoinj.** { *; }
-dontwarn org.bitcoinj.**

# ---------------------------------
# Keep SLF4J logging (used by BitcoinJ)
# ---------------------------------
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**
-keep class org.slf4j.impl.** { *; }
-dontwarn org.slf4j.impl.**

# ---------------------------------
# View constructors
# ---------------------------------
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ---------------------------------
# Keep enums
# ---------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------------------------------
# Keep Parcelable implementations
# ---------------------------------
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ---------------------------------
# Keep Serializable classes
# ---------------------------------
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
