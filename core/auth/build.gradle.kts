plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.tuji.android.core.auth"
    // compileSdk is the newest platform available; targetSdk is what Play's
    // policy requires. They are deliberately different numbers.
    compileSdk = 37
    compileSdkMinor = 1
    // 29, not the plan's 26 — see app/build.gradle.kts and docs/SPIKE-FURIGANA.md.
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:model"))
    // core:network owns AccessTokenProvider; this module supplies the witness.
    api(project(":core:network"))

    api(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)

    // Google sign-in. Credential Manager is the current standard — the old
    // GoogleSignIn SDK the iOS app's counterpart uses has no Android successor
    // by that name.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.identity.googleid)

    // Apple has no native Android sign-in, so it goes through a Custom Tab.
    implementation(libs.androidx.browser)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
