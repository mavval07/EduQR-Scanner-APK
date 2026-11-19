plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.eduqr.scanner"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.eduqr.scanner"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Google ML Kit Barcode Scanning (100% offline di perangkat)
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // OkHttp untuk pengiriman Wi-Fi LAN cepat ke Server PC EduQR
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
