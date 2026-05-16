# SafeStrideAI ProGuard Rules

# App specific
-keep class com.pmgaurav.safestrideai.** { *; }

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keepattributes *Annotation*
-keepattributes Signature

# Firebase
-keep class com.google.firebase.** { *; }
-keepattributes *Annotation*

# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# Gson
-keep class com.google.gson.** { *; }

# ARCore
-keep class com.google.ar.core.** { *; }

# SceneView
-keep class io.github.sceneview.** { *; }

# General
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-dontwarn android.hardware.camera2.**
