plugins {
    alias(libs.plugins.anya.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ai.anya.companion"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.network)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)

    implementation(projects.feature.pairing)
    implementation(projects.feature.sessions)
    implementation(projects.feature.chat)
    implementation(projects.feature.approval)
    implementation(projects.feature.workspace)
    implementation(projects.feature.settings)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}
