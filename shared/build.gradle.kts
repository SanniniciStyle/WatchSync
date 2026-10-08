plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "it.sanninicistyle.watchsync.shared"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.play.services.wearable)
    api(libs.kotlinx.coroutines.play.services)
    api(libs.kotlinx.serialization.protobuf)
    implementation(libs.androidx.core.ktx)
}
