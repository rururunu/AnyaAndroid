plugins {
    alias(libs.plugins.anya.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ai.anya.companion.core.network"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(project(":core:domain"))

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    implementation(libs.timber)
    ksp(libs.hilt.compiler)
}
