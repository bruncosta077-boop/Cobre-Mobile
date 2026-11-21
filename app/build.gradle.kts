plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "app.cobre.mobile"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.cobre.mobile"
        minSdk = 29          // loadThumbnail() e MediaStore moderno; ver STATUS.md
        targetSdk = 34
        versionCode = 18
        versionName = "0.16.0"
    }
    buildFeatures { compose = true; buildConfig = true }
    val mobileKeystorePath = project.providers.environmentVariable("COBRE_MOBILE_KEYSTORE_PATH").orNull
    val mobileStorePassword = project.providers.environmentVariable("COBRE_MOBILE_STORE_PASSWORD").orNull
    val mobileKeyAlias = project.providers.environmentVariable("COBRE_MOBILE_KEY_ALIAS").orNull
    val mobileKeyPassword = project.providers.environmentVariable("COBRE_MOBILE_KEY_PASSWORD").orNull
    val hasStableSigning = listOf(mobileKeystorePath, mobileStorePassword, mobileKeyAlias, mobileKeyPassword).all { !it.isNullOrBlank() }
    if (hasStableSigning) {
        signingConfigs.create("distribution") {
            storeFile = file(mobileKeystorePath!!)
            storePassword = mobileStorePassword
            keyAlias = mobileKeyAlias
            keyPassword = mobileKeyPassword
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasStableSigning) signingConfig = signingConfigs.getByName("distribution")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
