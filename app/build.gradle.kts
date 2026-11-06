plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.hiople.fotodb"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.hiople.fotodb"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.androidx.exifinterface)
    implementation(libs.gifDecoder)
    implementation(libs.opencc4j)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}