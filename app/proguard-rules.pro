# Project-specific R8 rules. Libraries ship their own consumer rules.
# Type-safe navigation routes are @Serializable; kotlinx-serialization ships consumer rules for them.

# LiteRT/TFLite native code looks up Java classes and methods by name (JNI); keep them intact
# so the minified release cannot crash at the first interpreter call (Phase 10 security audit).
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn org.tensorflow.lite.**

# ML Kit resolves its components (registrars, factories) by class at runtime; R8 class merging /
# renaming broke that in 0.1.0 (NPE in MlKitPlateReader.<init> on "İzlemeyi başlat").
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.firebase.components.** { *; }
-dontwarn com.google.mlkit.**
