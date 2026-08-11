plugins {
    alias(libs.plugins.anya.jvm.library)
}

kotlin {
    explicitApi()
}

dependencies {
    implementation("javax.inject:javax.inject:1")
}
