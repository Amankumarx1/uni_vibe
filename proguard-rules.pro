# kotlinx.serialization / Retrofit rules (only needed if you enable minification)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.univibe.app.data.** { *** Companion; }
-keepclasseswithmembers class com.univibe.app.data.** { kotlinx.serialization.KSerializer serializer(...); }
