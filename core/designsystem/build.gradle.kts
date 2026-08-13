plugins {
    alias(libs.plugins.anya.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ai.anya.companion.core.designsystem"
    buildFeatures {
        compose = true
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons)
    api(libs.mikepenz.markdown)
    api(libs.mikepenz.markdown.m3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.haze)
    debugApi(libs.androidx.compose.ui.tooling)
}