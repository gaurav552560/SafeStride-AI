// plugins {
//    alias(libs.plugins.android.application)
//    alias(libs.plugins.kotlin.android)
//    alias(libs.plugins.ksp)
//    alias(libs.plugins.google.services)
//    alias(libs.plugins.crashlytics)
// }
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
 
android { 
    namespace = "com.pmgaurav.safestrideai" 
    compileSdk = 35
 
    defaultConfig { 
        applicationId = "com.pmgaurav.safestrideai" 
        minSdk = 24 
        targetSdk = 35 
        versionCode = 1 
        versionName = "1.0" 
        multiDexEnabled = true 
        ndk { 
            abiFilters += listOf("arm64-v8a", "armeabi-v7a") 
        } 
    } 

    splits {
        abi {
            isEnable = false
        }
    }
 
    buildTypes { 
        release { 
            isMinifyEnabled = true 
            isShrinkResources = true 
            proguardFiles( 
                getDefaultProguardFile("proguard-android-optimize.txt"), 
                "proguard-rules.pro",
            ) 
            packaging {
                jniLibs {
                    useLegacyPackaging = false
                }
            }
        } 
        debug { 
            isDebuggable = true 
            packaging {
                jniLibs {
                    useLegacyPackaging = false
                }
            }
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
        viewBinding = true 
        buildConfig = true
    }

    androidResources {
        noCompress += "tflite"
    }

    // composeOptions {
    //    kotlinCompilerExtensionVersion = "1.5.3"
    // }

    packaging {
        resources { 
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "kotlin/**",
                "**.proto",
                "okhttp3/**",
                "/META-INF/{AL2.0,LGPL2.1}"
            )
        } 
        jniLibs { 
            useLegacyPackaging = false
        } 
    }
} 
 
dependencies { 
    implementation(libs.androidx.core.ktx) 
    implementation(libs.androidx.lifecycle.runtime.ktx) 
    implementation(libs.androidx.lifecycle.viewmodel.ktx) 
    implementation(libs.androidx.lifecycle.runtime.compose) 
    implementation(libs.androidx.activity.compose) 
    implementation(platform(libs.androidx.compose.bom)) 
    implementation(libs.androidx.ui) 
    implementation(libs.androidx.ui.graphics) 
    implementation(libs.androidx.ui.tooling.preview) 
    implementation(libs.androidx.material3) 
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling) 
    implementation(libs.androidx.camera.core) 
    implementation(libs.androidx.camera.camera2) 
    implementation(libs.androidx.camera.lifecycle) 
    implementation(libs.androidx.camera.view) 
    implementation(libs.tensorflow.lite) 
    implementation(libs.tensorflow.lite.gpu) 
    implementation(libs.tensorflow.lite.gpu.api)
    implementation(libs.tensorflow.lite.api) 
    implementation(libs.tensorflow.lite.support)
    implementation(libs.opencv)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.accompanist.permissions)

    // New dependencies
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.arcore)
    implementation(libs.sceneview)
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.bouncycastle.tls)
    implementation(libs.play.services.wearable)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
