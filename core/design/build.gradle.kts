plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.tuji.android.core.design"
    // compileSdk is the newest platform available; targetSdk is what Play's
    // policy requires. They are deliberately different numbers — updating the
    // one you compile against is not the same decision as opting in to a new
    // release's runtime behaviour, and androidx now forces the first.
    compileSdk = 37
    compileSdkMinor = 1
    // 29, not the plan's 26 — see app/build.gradle.kts and docs/SPIKE-FURIGANA.md.
    defaultConfig { minSdk = 29 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


dependencies {
    api(project(":core:model"))

    implementation(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
