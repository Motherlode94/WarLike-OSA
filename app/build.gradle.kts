plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.warlike.osa"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.warlike.osa"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("ciRelease") {
            val store = providers.environmentVariable("WARLIKE_KEYSTORE_PATH").orNull
            val storePass = providers.environmentVariable("WARLIKE_KEYSTORE_PASSWORD").orNull
            val alias = providers.environmentVariable("WARLIKE_KEY_ALIAS").orNull
            val keyPass = providers.environmentVariable("WARLIKE_KEY_PASSWORD").orNull
            if (!store.isNullOrBlank() && !storePass.isNullOrBlank() && !alias.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                storeFile = file(store)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (!providers.environmentVariable("WARLIKE_KEYSTORE_PATH").orNull.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("ciRelease")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
