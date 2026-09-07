// AGP 9 carries Kotlin support itself, so there is no `kotlin.android` plugin
// here: applying it is now an error, not a redundancy. The three that remain are
// genuine compiler plugins (Compose, serialization) plus the JVM plugin the one
// pure-Kotlin module needs.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
