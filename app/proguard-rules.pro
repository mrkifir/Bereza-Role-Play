# kotlinx.serialization постачає власні consumer-правила.
# Зберігаємо моделі маніфесту, конфігурації та новин.
-keep,includedescriptorclasses class ua.bereza.launcher.model.** { *; }
-keepclassmembers class ua.bereza.launcher.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
