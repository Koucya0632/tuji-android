plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin, like `core:study` and `core:catalog`, and for the same reason.
// What lives here are 物見's decisions — who is hidden, what a 檢舉 is about —
// and none of them needs a device to be true.
//
// A third module rather than a folder in one of the other two: 物見 is neither
// 學習 nor 圖鑑. The one thing all three share is `core:model`.
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
    testImplementation(libs.junit)
}
