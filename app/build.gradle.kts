plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.koinupapi.bastion"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.koinupapi.bastion"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.0"
    }
    signingConfigs {
        create("bastion") {
            val keystorePath = System.getenv("BASTION_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("BASTION_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BASTION_KEY_ALIAS")
                keyPassword = System.getenv("BASTION_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("bastion")
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
