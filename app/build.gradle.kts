import java.util.Properties
import java.io.FileInputStream
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.memely"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.memely"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0.3"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        languageVersion = "1.8"
        apiVersion = "1.8"
    }

    signingConfigs {
        create("release") {
            // Load keystore properties from secure file
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                
                storeFile = file(keystoreProperties["storeFile"].toString())
                storePassword = keystoreProperties["storePassword"].toString()
                keyAlias = keystoreProperties["keyAlias"].toString()
                keyPassword = keystoreProperties["keyPassword"].toString()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val buildType = variant.buildType.name
            val newName = "memely-v${defaultConfig.versionName}-${buildType}.apk"
            output.outputFileName = newName
        }
    }

    bundle {
        density.enableSplit = true
        language.enableSplit = true
    }
}

tasks.all {
    if (name == "bundleRelease") {
        doLast {
            val bundleFile = File("${layout.buildDirectory.get()}/outputs/bundle/release/app-release.aab")
            val newBundleFile = File("${layout.buildDirectory.get()}/outputs/bundle/release/memely-v${android.defaultConfig.versionName}.aab")
            if (bundleFile.exists() && !newBundleFile.exists()) {
                bundleFile.renameTo(newBundleFile)
                println("Renamed bundle to: memely-v${android.defaultConfig.versionName}.aab")
            }
        }
    }
}

// Ensure Kotlin compile tasks target a supported JVM version (fixes kapt/jvmTarget issues)
tasks.withType(KotlinCompile::class.java).configureEach {
    kotlinOptions {
        jvmTarget = "17"
        // Use the newer JVM default compatibility flag
        freeCompilerArgs += listOf("-Xjvm-default=all-compatibility")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2023.10.00")
    implementation(composeBom)

    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material")
    implementation("androidx.navigation:navigation-compose:2.7.4")

    // Accompanist
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")
    implementation("com.google.accompanist:accompanist-flowlayout:0.32.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // JSON
    implementation("org.json:json:20230618")

    // Coil
    implementation("io.coil-kt:coil-compose:2.4.0")

    // Material Icons
    implementation("androidx.compose.material:material-icons-extended")

    // BitcoinJ only (for Bech32 if needed, no JNI)
    implementation("org.bitcoinj:bitcoinj-core:0.15.10")

    // Secp256k1 for Schnorr signatures (BIP-340) - Android version with JNI
    implementation("fr.acinq.secp256k1:secp256k1-kmp:0.10.1")
    implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-android:0.10.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
