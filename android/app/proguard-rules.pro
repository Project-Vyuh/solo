# Solo proguard rules
# Keep all JNI bridge classes — they are referenced from native code
-keep class dev.projectvyuh.solo.data.llm.jni.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
