plugins {
    alias(libs.plugins.anya.android.feature)
}

android {
    namespace = "ai.anya.companion.feature.chat"
}

dependencies {
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.haze)
    implementation(libs.androidx.activity.compose)
}
