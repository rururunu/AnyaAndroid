-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

-keep class ai.anya.companion.core.model.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
