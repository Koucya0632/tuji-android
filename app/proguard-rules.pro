# R8 rules for the release build.
#
# Kept small on purpose: a keep rule that is not needed is dead weight that
# nobody can safely remove later, because "does anything still break without
# this?" costs a full release build to answer.

# kotlinx.serialization generates a `Companion.serializer()` per @Serializable
# class and reaches it reflectively at the entry point. R8's shrinker cannot see
# that edge, and the failure mode is a runtime SerializationException in a
# release build only — which is to say, only in front of users.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}

# Ktor picks its engine through a ServiceLoader.
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { *; }
-keepnames class io.ktor.** { *; }
-dontwarn org.slf4j.**

# OkHttp's optional Conscrypt / BouncyCastle providers are referenced but absent.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
