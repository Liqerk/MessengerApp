plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization") version "1.9.21"
    kotlin("kapt") version "1.9.21"
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += ""
                arguments += "-DANDROID_STL=c++_shared"
            }
        }

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
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

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/libs")
        }
    }
}

// === Автосборка hev-socks5-tunnel ===
afterEvaluate {
    val ndkDir = android.ndkDirectory
    val mainDir = file("src/main")

    tasks.register<Exec>("buildTun2Socks") {
        workingDir = mainDir
        commandLine(
            "$ndkDir/ndk-build",
            "NDK_PROJECT_PATH=${mainDir.absolutePath}",
            "APP_BUILD_SCRIPT=${mainDir.absolutePath}/jni/Android.mk",
            "NDK_APPLICATION_MK=${mainDir.absolutePath}/jni/Application.mk",
            "NDK_LIBS_OUT=${mainDir.absolutePath}/jniLibs"
        )
    }

    tasks.named("preBuild") {
        dependsOn("buildTun2Socks")
    }
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    // ========== СЕТЬ ==========
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ========== JSON ==========
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // ========== COROUTINES ==========
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // ========== GLIDE ==========
    implementation("com.github.bumptech.glide:glide:4.16.0")

    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // ========== UI ==========
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.11.0")

    // ========== LIFECYCLE ==========
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    // ========== ByeDPI ==========
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // ========== ROOM ==========
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")

    // ========== CORE ==========
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.annotation)

    // ========== TESTS ==========
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.google.android.apps.common.testing.accessibility.framework:accessibility-test-framework:7.1.0")
}