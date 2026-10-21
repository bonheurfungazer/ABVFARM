import java.time.Duration
import java.util.Base64

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.ksp)
    alias(libs.plugins.googleServices)
}

android {
    namespace = "com.example"
    compileSdk = 34

    // Decode the persistent base64 keystore if it does not exist yet
    val keystoreFile = File(project.rootDir, "debug.keystore")
    val base64File = File(project.rootDir, "debug.keystore.base64")
    if (!keystoreFile.exists() && base64File.exists()) {
        try {
            val base64Bytes = base64File.readBytes()
            val cleanedBytes = base64Bytes.filter { it != '\r'.code.toByte() && it != '\n'.code.toByte() && it != ' '.code.toByte() }.toByteArray()
            val decodedBytes = Base64.getDecoder().decode(cleanedBytes)
            keystoreFile.writeBytes(decodedBytes)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    signingConfigs {
        create("shared") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeFile = File(project.rootDir, "debug.keystore")
            storePassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.aistudio.abvfarm.xqyztg"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
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
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.all {
            it.failFast = true
            it.timeout.set(Duration.ofMinutes(2))
            it.testLogging {
                events("passed", "failed", "skipped", "standardError")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Firebase & Google Credentials
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
