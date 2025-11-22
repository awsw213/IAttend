plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.iattend"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.iattend"
        minSdk = 28
        targetSdk = 34
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = false
    }
}


dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    

    

    // HTTP logging (exclude OkHttp to avoid potential duplication with vendor AAR)
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }

    // OkHttp core
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Supabase
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.0.0")
    implementation("io.github.jan-tennert.supabase:storage-kt:3.0.0")
    implementation("io.github.jan-tennert.supabase:functions-kt:3.0.0")
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")

    // Image loading with Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // ViewModel and LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.7.0")

    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.androidx.appcompat)
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(project(":facelib"))
    

    implementation("com.amap.api:3dmap:10.0.600") {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
        exclude(group = "com.squareup.okio", module = "okio")
        exclude(group = "com.google.code.gson", module = "gson")
    }

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    
}
