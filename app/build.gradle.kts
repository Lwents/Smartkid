import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}

// VPS công khai — luôn dùng làm phương án dự phòng khi không có backend chạy trên máy.
// Đi qua nginx (cổng 80), không gọi thẳng gunicorn cổng 8000.
val vpsApiBaseUrl = "http://160.250.181.242/api/"
// URL local ưu tiên: lấy từ -PAPI_BASE_URL hoặc local.properties, mặc định là loopback của
// emulator. App sẽ tự probe URL này lúc khởi động; nếu không kết nối được thì chuyển sang VPS.
val localApiBaseUrl = providers.gradleProperty("API_BASE_URL").orNull
    ?: localProperties.getProperty("API_BASE_URL")
    ?: "http://10.0.2.2:8000/api/"

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
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${localApiBaseUrl.trimEnd('/')}/\""
        )
        buildConfigField(
            "String",
            "API_FALLBACK_URL",
            "\"${vpsApiBaseUrl.trimEnd('/')}/\""
        )
    }

    buildTypes {
        release {
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
                "src/main/res-profile",
                "src/main/res-notification",
                "src/main/res-student-home",
                "src/main/res-course",
                "src/main/res-exam",
                "src/main/res-ai",
                "src/main/res-payment",
                "src/main/res-role-common",
                "src/main/res-content-authoring",
                "src/main/res-teacher",
                "src/main/res-admin"
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
    implementation(libs.youtube.player)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
