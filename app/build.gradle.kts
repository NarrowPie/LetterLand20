plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.letterland"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }

    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }


    defaultConfig {
        applicationId = "com.example.letterland"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // 🌟 UPDATED: Removed Text Recognition, Added Digital Ink Recognition 🌟
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:digital-ink-recognition:18.1.0")
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")
    // For saving images easily, we'll use a helper
    implementation("commons-io:commons-io:2.11.0")
}