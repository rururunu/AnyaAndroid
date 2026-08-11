plugins {
    alias(libs.plugins.anya.jvm.library)
}

kotlin {
    explicitApi()
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    implementation("javax.inject:javax.inject:1")
}
