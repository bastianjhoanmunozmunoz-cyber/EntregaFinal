import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val mapsSecrets = Properties().apply {
    val secretsFile = rootProject.file("secrets.properties")
    if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.example.mapgps"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.mapgps"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Orden de búsqueda: secrets.properties -> -PMAPS_API_KEY -> variable de entorno.
        manifestPlaceholders["MAPS_API_KEY"] = mapsSecrets.getProperty("MAPS_API_KEY")
            ?: (project.findProperty("MAPS_API_KEY") as String?)
            ?: System.getenv("MAPS_API_KEY")
            ?: "YOUR_API_KEY"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.security.crypto) // cifrado de bitácora y fotos (Android Keystore)
    testImplementation(libs.junit)
    testImplementation(libs.org.json) // org.json real para pruebas locales (android.jar solo trae stubs)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
