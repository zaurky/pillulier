plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "fr.pillulier.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.pillulier.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")
    sourceSets["androidTest"].kotlin.srcDir("src/androidTest/kotlin")

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    // Les schemas vivent dans les assets de la variante main, et non dans
    // $projectDir/schemas : AGP 8.12.0 fait pointer `android_merged_assets` des
    // tests unitaires sur les assets fusionnes de main, et ne fusionne jamais
    // ceux du source set `test`. C'est le seul emplacement ou
    // MigrationTestHelper les trouve en JVM. Ils pesent une vingtaine de Ko
    // dans l'APK, prix accepte pour une migration testee.
    arg("room.schemaLocation", "$projectDir/src/main/assets")
}

dependencies {
    implementation(project(":domain"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.ext.compiler)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.glance.appwidget.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
}
