# kotlinx.serialization: keep generated serializers for backup models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.apex.notes.backup.**$$serializer { *; }
-keepclassmembers class dev.apex.notes.backup.** { *** Companion; }
-keepclasseswithmembers class dev.apex.notes.backup.** { kotlinx.serialization.KSerializer serializer(...); }
