plugins {
    alias(libs.plugins.anya.android.feature)
}

android {
    namespace = "ai.anya.companion.feature.chat"
}

dependencies {
    testImplementation(libs.junit)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.androidx.activity.compose)
}
