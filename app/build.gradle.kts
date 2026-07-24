plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.smartkid"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.smartkid"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"http://160.250.181.242:8000/api/\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://160.250.181.242:8000/api/\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"http://160.250.181.242:8000/api/\"")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            res.srcDirs(
                "src/main/res",
                "src/main/res-auth",
                "src/main/res-home",
                "src/main/res-course",
                "src/main/res-exam",
                "src/main/res-ai",
                "src/main/res-profile",
                "src/main/res-payment",
                "src/main/res-notification",
                "src/main/res-management",
                "src/main/res-admin",
                "src/main/res-teacher"
            )
        }
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.volley)
    implementation(libs.picasso)
    implementation(libs.viewpager2)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
