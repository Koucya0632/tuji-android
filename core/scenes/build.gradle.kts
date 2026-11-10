plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// 場面練習's rules, pure Kotlin like `core:study`: the content as the bundled
// JSON writes it, where an account stands on each 問句, what a pass practises
// and the machines a 場面 hands over. Reading the JSON out of the APK and
// drawing it is the app's job; everything that decides is here and tested on
// the JVM. It mirrors tuji-ios's `Core/Scenes`, so a rule changed there is
// changed here by the same name.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
