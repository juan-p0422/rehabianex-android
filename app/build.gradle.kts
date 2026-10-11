plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

// The Firebase config is environment-specific and must be supplied outside source control.
// Keeping the plugin conditional lets non-Firebase builds compile without a fake config file.
if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

val officialRenderApiBaseUrl = "https://rehabianex-api.onrender.com/api/"
val configuredApiBaseUrl = providers.gradleProperty("REHABIANEX_API_BASE_URL")
    .orElse(officialRenderApiBaseUrl)
    .get()
    .trim()
require(officialRenderApiBaseUrl.startsWith("https://") && officialRenderApiBaseUrl.endsWith("/")) {
    "La URL de Render debe usar HTTPS y terminar en /."
}
require(configuredApiBaseUrl == officialRenderApiBaseUrl) {
    "REHABIANEX_API_BASE_URL solo admite la API oficial de Render."
}

android {
    namespace = "com.example.rehabianex"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.rehabianex"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$configuredApiBaseUrl\"")
            buildConfigField("boolean", "ADMIN_PANEL_ENABLED", "true")
        }
        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "API_BASE_URL", "\"$configuredApiBaseUrl\"")
            buildConfigField("boolean", "ADMIN_PANEL_ENABLED", "true")
            matchingFallbacks += listOf("debug")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$configuredApiBaseUrl\"")
            buildConfigField("boolean", "ADMIN_PANEL_ENABLED", "false")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.retrofit.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
}
