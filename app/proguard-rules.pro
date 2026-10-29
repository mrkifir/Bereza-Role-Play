# kotlinx.serialization постачає власні consumer-правила.
# Зберігаємо моделі маніфесту, конфігурації та новин.
-keep,includedescriptorclasses class ua.bereza.launcher.data.model.** { *; }
-keepclassmembers class ua.bereza.launcher.data.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
