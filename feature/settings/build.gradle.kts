plugins {
    alias(libs.plugins.anya.android.feature)
}

android {
    namespace = "ai.anya.companion.feature.settings"
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
