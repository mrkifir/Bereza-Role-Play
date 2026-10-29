import java.io.InputStreamReader
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---- Конфігурація з bereza.properties ----
val berezaProps = Properties().apply {
    val f = rootProject.file("bereza.properties")
    if (f.exists()) {
        f.inputStream().use { load(InputStreamReader(it, Charsets.UTF_8)) }
    }
}

fun cfg(key: String, default: String = ""): String =
    berezaProps.getProperty(key, default).trim()

fun quoted(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// ---- Підпис релізу (необов'язково) ----
// Якщо задано змінні середовища BEREZA_KEYSTORE (шлях до .jks) та BEREZA_KEY_PASSWORD,
// release-збірка підписується цим ключем (потрібно для оновлень поверх старої версії).
val keystorePath: String? = System.getenv("BEREZA_KEYSTORE")
val keystorePassword: String? = System.getenv("BEREZA_KEY_PASSWORD")
val hasReleaseKey = keystorePath != null && keystorePassword != null && file(keystorePath).exists()

android {
    namespace = "ua.bereza.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "ua.bereza.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = cfg("launcher.versionCode", "1").toInt()
        versionName = cfg("launcher.versionName", "1.0.0")

        buildConfigField("String", "SERVER_NAME", quoted(cfg("server.name", "Bereza Role Play")))
        buildConfigField("String", "SERVER_HOST", quoted(cfg("server.host")))
        buildConfigField("int", "SERVER_PORT", cfg("server.port", "7777").toInt().toString())
        buildConfigField("String", "CLIENT_PACKAGE", quoted(cfg("client.package")))
        buildConfigField("String", "CLIENT_NAME", quoted(cfg("client.name", "SA-MP client")))
        buildConfigField("boolean", "NICK_RP_FORMAT", cfg("nickname.rpFormat", "true").toBoolean().toString())
        buildConfigField("String", "NEWS_URL", quoted(cfg("news.url")))
        buildConfigField("String", "UPDATE_URL", quoted(cfg("update.url")))
        buildConfigField("String", "LINK_DISCORD", quoted(cfg("links.discord")))
        buildConfigField("String", "LINK_SITE", quoted(cfg("links.site")))
        buildConfigField("String", "LINK_TELEGRAM", quoted(cfg("links.telegram")))
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("bereza") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = "bereza"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("bereza")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
