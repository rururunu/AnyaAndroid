plugins {
    alias(libs.plugins.anya.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    explicitApi()
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
