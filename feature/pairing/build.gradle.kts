plugins {
    alias(libs.plugins.anya.android.feature)
}

android {
    namespace = "ai.anya.companion.feature.pairing"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
