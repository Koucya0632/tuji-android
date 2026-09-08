import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Secrets live outside version control, exactly as they do on iOS
// (Config/Secrets.xcconfig). `secrets.properties.example` is the checked-in
// template. A missing file is not fatal at configure time — it fails at the
// point of use instead, so a fresh clone can still run `./gradlew test`.
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(key: String, fallback: String = ""): String =
    (secrets.getProperty(key) ?: System.getenv(key) ?: fallback)

android {
    namespace = "app.tuji.android"
    // compileSdk is the newest platform available; targetSdk is what Play's
    // policy requires. They are deliberately different numbers — updating the
    // one you compile against is not the same decision as opting in to a new
    // release's runtime behaviour, and androidx now forces the first.
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "app.tuji.android"
        // 29 = Android 10. The architecture plan said 26, and the M0 日文排版
        // spike is what moved it: `Typeface.CustomFallbackBuilder` is API 29,
        // and it is the only way to keep Latin in Plus Jakarta while CJK comes
        // from GenSenRounded. Below 29 the choice is a wrong CJK face or — far
        // worse — a wrong Latin face across the whole app (ADR-0003).
        //
        // Costs 5 points of global device reach (96.1% → 91.1%). Cheap next to
        // what iOS already asks: `IPHONEOS_DEPLOYMENT_TARGET = 18.0` is a 2024
        // OS and drops the iPhone X outright. Android 10 is from 2019.
        // See docs/SPIKE-FURIGANA.md.
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TUJI_BASE_URL", "\"${secret("TUJI_BASE_URL", "https://everyday-english-picture-dictionary.vercel.app")}\"")
        buildConfigField("String", "TUJI_SUPABASE_URL", "\"${secret("TUJI_SUPABASE_URL")}\"")
        buildConfigField("String", "TUJI_SUPABASE_ANON_KEY", "\"${secret("TUJI_SUPABASE_ANON_KEY")}\"")
        // The **web** client ID. Credential Manager wants that one, not the
        // Android client — the Android client exists only so Google can
        // check the calling app's signing certificate.
        buildConfigField("String", "TUJI_GOOGLE_WEB_CLIENT_ID", "\"${secret("TUJI_GOOGLE_WEB_CLIENT_ID")}\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}


dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:design"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
