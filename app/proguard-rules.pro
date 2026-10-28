# Project-specific R8 rules. Libraries ship their own consumer rules.
# Type-safe navigation routes are @Serializable; kotlinx-serialization ships consumer rules for them.

# LiteRT/TFLite native code looks up Java classes and methods by name (JNI); keep them intact
# so the minified release cannot crash at the first interpreter call (Phase 10 security audit).
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn org.tensorflow.lite.**
