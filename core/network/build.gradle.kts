plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.tuji.android.core.network"
    // compileSdk is the newest platform available; targetSdk is what Play's
    // policy requires. They are deliberately different numbers — updating the
    // one you compile against is not the same decision as opting in to a new
    // release's runtime behaviour, and androidx now forces the first.
    compileSdk = 37
    compileSdkMinor = 1
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}


dependencies {
    api(project(":core:model"))

    api(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
