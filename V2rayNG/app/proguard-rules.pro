# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# ---- SatraNG release shrinking (R8 full mode) ----
# No renaming: Gson, MMKV keys, intents and logs rely on names; shrinking + optimizing still apply.
-dontobfuscate
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable
# Gson models: keep every field and constructor of app classes (serialized by reflection).
-keepclassmembers class com.v2ray.ang.** { <fields>; <init>(...); }
-keep class com.v2ray.ang.dto.** { *; }
-keep enum com.v2ray.ang.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# gomobile core (JNI both ways) and its callbacks implemented in the app.
-keep class go.** { *; }
-keep class libv2ray.** { *; }
-keep class * implements libv2ray.** { *; }
# hev-socks5-tunnel JNI.
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
-keep class com.v2ray.ang.service.TProxyService { *; }
# MMKV / WorkManager workers instantiated by name.
-keep class com.tencent.mmkv.** { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
