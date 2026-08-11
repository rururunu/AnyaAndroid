plugins {
    `kotlin-dsl`
}

group = "ai.anya.companion.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "anya.android.application"
            implementationClass = "ai.anya.companion.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "anya.android.library"
            implementationClass = "ai.anya.companion.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "anya.android.feature"
            implementationClass = "ai.anya.companion.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "anya.jvm.library"
            implementationClass = "ai.anya.companion.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
