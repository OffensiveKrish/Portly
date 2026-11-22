import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        FileInputStream(versionPropsFile).use { load(it) }
    }
}
val appVersionName: String = versionProps.getProperty("VERSION_NAME", "2026.11.0")
val appVersionCode: Int = versionProps.getProperty("VERSION_CODE", "202611000").toInt()

android {
    namespace = "com.portly.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.portly.app"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "GITHUB_REPO_OWNER", "\"OffensiveKrish\"")
        buildConfigField("String", "GITHUB_REPO_NAME", "\"Portly\"")
        buildConfigField("String", "EXPECTED_SIGNING_CERT_SHA256", "\"09637AB91C8C71A5BFCCD4E3E7F6A552991B9F14153D84F68A67E08F22D01D1A\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val envPath = System.getenv("PORTLY_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
                ?: System.getenv("ANDROID_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
            val keystoreFile = if (!envPath.isNullOrBlank()) file(envPath) else file("release-test.keystore")
            storeFile = if (keystoreFile.exists()) keystoreFile else file("release-test.keystore")
            storePassword = System.getenv("PORTLY_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
                ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
                ?: "portly-release-test"
            keyAlias = System.getenv("PORTLY_KEY_ALIAS")?.takeIf { it.isNotBlank() }
                ?: System.getenv("ANDROID_KEY_ALIAS")?.takeIf { it.isNotBlank() }
                ?: "portly-test"
            keyPassword = System.getenv("PORTLY_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                ?: System.getenv("ANDROID_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                ?: "portly-release-test"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/DEPENDENCIES",
                "META-INF/*.kotlin_module",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-security"))
    implementation(project(":core:core-database"))
    implementation(project(":core:core-datastore"))
    implementation(project(":core:core-ui"))

    implementation(project(":transfer:transfer-engine"))
    implementation(project(":network:network-manager"))

    implementation(project(":protocol:protocol-ftp-client"))
    implementation(project(":protocol:protocol-ftps-client"))
    implementation(project(":protocol:protocol-sftp-client"))
    implementation(project(":protocol:protocol-webdav-client"))
    implementation(project(":protocol:protocol-smb-client"))
    implementation(project(":protocol:protocol-ftp-server"))
    implementation(project(":protocol:protocol-ftps-server"))
    implementation(project(":protocol:protocol-http-server"))
    implementation(project(":protocol:protocol-https-server"))

    implementation(project(":feature:feature-home"))
    implementation(project(":feature:feature-filemanager"))
    implementation(project(":feature:feature-connections"))
    implementation(project(":feature:feature-servers"))
    implementation(project(":feature:feature-transfers"))
    implementation(project(":feature:feature-history"))
    implementation(project(":feature:feature-settings"))
    implementation(project(":feature:feature-preview"))
    implementation(project(":feature:feature-archive"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.androidx.hilt.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.nav.suite)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
