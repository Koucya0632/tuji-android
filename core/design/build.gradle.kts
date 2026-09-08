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
    // The option-state decision lives in core:study; this module only colours it.
    api(project(":core:study"))

    implementation(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    // Nuke on iOS. The signed-URL cache key it needs is a later problem
    // (see the plan): a private-bucket photo re-signs on every response,
    // so a whole-URL key misses forever on the user's own pictures.
    api(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
