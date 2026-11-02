# Keep the generated kotlinx.serialization serializers of the SDK's wire models.
-keepclassmembers class io.github.keshavop.featureflags.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.keshavop.featureflags.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.keshavop.featureflags.**$$serializer { *; }
