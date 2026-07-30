import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val apkBaseName = "adventurer-guild"
val privateProperties = Properties().apply {
    val privateFile = rootProject.file("private.properties")
    if (privateFile.exists()) {
        privateFile.inputStream().use(::load)
    }
}

fun privateSetting(name: String): String =
    providers.environmentVariable(name).orNull
        ?: privateProperties.getProperty(name).orEmpty()

fun String.asBuildConfigString(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.example.adventurerguild"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.adventurerguild"
        minSdk = 24
        targetSdk = 35
        versionCode = 9
        versionName = "0.2.0"
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${privateSetting("GOOGLE_WEB_CLIENT_ID").asBuildConfigString()}\""
        )
        buildConfigField(
            "String",
            "CLOUDFLARE_API_BASE_URL",
            "\"${privateSetting("CLOUDFLARE_API_BASE_URL").asBuildConfigString()}\""
        )
        buildConfigField("Boolean", "ENABLE_TEST_ACCOUNTS", "false")
    }

    buildTypes {
        debug {
            buildConfigField("Boolean", "ENABLE_TEST_ACCOUNTS", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    applicationVariants.all {
        val variantName = name
        val variantVersionName = versionName
        outputs.all {
            val apkOutput =
                this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            apkOutput.outputFileName =
                "$apkBaseName-$variantVersionName-$variantName.apk"
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
