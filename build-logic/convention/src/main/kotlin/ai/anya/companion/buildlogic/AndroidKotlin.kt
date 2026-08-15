package ai.anya.companion.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal const val AnyaCompileSdk = 36
internal const val AnyaMinSdk = 26
internal const val AnyaTargetSdk = 36

internal val Project.libs
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension,
) {
    commonExtension.compileSdk = AnyaCompileSdk
    commonExtension.defaultConfig.minSdk = AnyaMinSdk
    commonExtension.defaultConfig.testInstrumentationRunner =
        "androidx.test.runner.AndroidJUnitRunner"
    commonExtension.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    commonExtension.compileOptions.targetCompatibility = JavaVersion.VERSION_17
    commonExtension.packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlin.time.ExperimentalTime",
            )
        }
    }
}

internal fun Project.configureCompose(
    commonExtension: CommonExtension,
) {
    commonExtension.buildFeatures.compose = true
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

    dependencies {
        val bom = libs.findLibrary("androidx-compose-bom").get()
        add("implementation", platform(bom))
        add("implementation", libs.findLibrary("androidx-compose-ui").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-graphics").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("implementation", libs.findLibrary("androidx-compose-material3").get())
        add("implementation", libs.findLibrary("androidx-compose-material-icons").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
    }
}

internal fun Project.configureAndroidHilt() {
    pluginManager.apply("com.google.dagger.hilt.android")
    pluginManager.apply("com.google.devtools.ksp")

    dependencies {
        add("implementation", libs.findLibrary("hilt-android").get())
        add("ksp", libs.findLibrary("hilt-compiler").get())
    }
}

internal fun ApplicationExtension.configureAppDefaults() {
    defaultConfig.targetSdk = AnyaTargetSdk
    defaultConfig.applicationId = "ai.anya.companion"
    defaultConfig.versionCode = 3
    defaultConfig.versionName = "0.1.2"
}

internal fun ApplicationExtension.configureReleaseSigning(project: Project) {
    val propsFile = project.rootProject.file("keystore.properties")
    if (!propsFile.isFile) {
        project.logger.warn("keystore.properties missing; release APK will be unsigned")
        return
    }
    val props = java.util.Properties().apply {
        propsFile.inputStream().use { load(it) }
    }
    val store = project.rootProject.file(props.getProperty("storeFile") ?: return)
    if (!store.isFile) {
        project.logger.warn("${store.path} missing; release APK will be unsigned")
        return
    }
    val storePw = props.getProperty("storePassword").orEmpty()
    val alias = props.getProperty("keyAlias").orEmpty()
    val keyPw = props.getProperty("keyPassword").orEmpty()
    if (storePw.isEmpty() || alias.isEmpty() || keyPw.isEmpty()) {
        project.logger.warn("incomplete keystore.properties; release APK will be unsigned")
        return
    }
    val releaseSigning = signingConfigs.create("release") {
        storeFile = store
        storePassword = storePw
        keyAlias = alias
        keyPassword = keyPw
        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
    }
    buildTypes.getByName("release").signingConfig = releaseSigning
}
