plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    val releaseStoreFile = providers.environmentVariable("RESTAURANT_MANAGEMENT_KEYSTORE_PATH").orNull
    val releaseStorePassword = providers.environmentVariable("RESTAURANT_MANAGEMENT_KEYSTORE_PASSWORD").orNull
    val releaseKeyAlias = providers.environmentVariable("RESTAURANT_MANAGEMENT_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("RESTAURANT_MANAGEMENT_KEY_PASSWORD").orNull
    val signingValues = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    val signingConfigured = signingValues.any { !it.isNullOrBlank() }
    val signingComplete = signingValues.all { !it.isNullOrBlank() }
    if (signingConfigured && !signingComplete) {
        throw GradleException(
            "Release signing is partially configured. Set all RESTAURANT_MANAGEMENT_* signing variables or none of them.",
        )
    }
    if (signingComplete && !file(requireNotNull(releaseStoreFile)).isFile) {
        throw GradleException("Release keystore does not exist at RESTAURANT_MANAGEMENT_KEYSTORE_PATH.")
    }

    namespace = "ir.restaurant.management"
    compileSdk = 36

    defaultConfig {
        applicationId = "ir.restaurant.management"
        minSdk = 23
        targetSdk = 36
        versionCode = 209
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (signingComplete) {
            create("releaseFromEnvironment") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            if (signingComplete) {
                signingConfig = signingConfigs.getByName("releaseFromEnvironment")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xconsistent-data-class-copy-visibility"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)
    // Room 2.8.4 room-migration is compiled for kotlinx.serialization 1.8.1.
    // Its generated serializers rely on the 1.8+ default GeneratedSerializer ABI.
    // Keep app/test runtime classloading on the exact Room-declared ABI; KSP remains untouched.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1") { version { strictly("1.8.1") } }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1") { version { strictly("1.8.1") } }
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.sqlite.framework)
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1") { version { strictly("1.8.1") } }
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1") { version { strictly("1.8.1") } }
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestUtil("androidx.test:orchestrator:1.6.1")
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
