# Shipped to the app's R8 run (consumerProguardFiles). MediaPipe Tasks ships no R8 rules of its own, but its native
# libraries look up these Java classes, fields and methods by name over JNI: if R8 renames or strips them, the model
# fails to load (NoSuchFieldError/NoSuchMethodError) only in release builds.
-keep class com.google.mediapipe.framework.** { *; }
-keep class com.google.mediapipe.tasks.core.** { *; }
-keep class com.google.mediapipe.tasks.text.** { *; }
-keep class com.google.mediapipe.tasks.components.** { *; }
-keep class com.google.mediapipe.proto.** { *; }
-keep class com.google.mediapipe.calculator.proto.** { *; }
-dontwarn com.google.mediapipe.**

# MediaPipe's Graph and PacketGetter log through Flogger, which finds its backend reflectively by class name;
# protobuf-lite messages are read via their generated fields. Neither library ships R8 rules.
-keep class com.google.common.flogger.backend.system.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
-dontwarn com.google.common.flogger.**
