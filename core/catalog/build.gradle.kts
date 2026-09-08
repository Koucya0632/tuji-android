plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin, like `core:study`, and for the same reason: what lives here is
// *decisions about the catalogue* — how a query ranks, which shelves exist —
// and none of that should need an Android runtime to be true or a device to be
// tested on.
//
// A separate module from `core:study` rather than a folder inside it. 圖鑑 is
// not 學習: nothing here reads an SRS rating and nothing there ranks a search.
// One module named for two subjects is how the next person ends up importing
// the whole study layer to sort a list.
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
