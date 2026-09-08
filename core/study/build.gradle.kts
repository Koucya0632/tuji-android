plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Pure Kotlin, no Android — deliberately.
//
// The architecture plan calls this the highest logic-density part of the port,
// and the thing it protects is data the user cannot get back. Keeping it off
// the Android plugin means its tests run on the JVM in seconds and that the
// durability rules cannot quietly acquire a platform dependency: the moment
// this needs a Context to be correct, it stops being testable at the speed
// anyone will actually run it.
//
// WorkManager belongs on the other side of this line. It triggers a drain; it
// is not where anything is stored.
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
    testImplementation(libs.kotlinx.coroutines.test)
}
