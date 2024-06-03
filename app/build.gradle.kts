plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rasamadev.varsign"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rasamadev.varsign"
        minSdk = 25
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    implementation("com.itextpdf:itextg:5.5.10")
    implementation("com.madgag:scpkix-jdk15on:1.47.0.2")
    implementation("com.madgag:scprov-jdk15on:1.47.0.2")

//    implementation("com.itextpdf.android:itext-core-android:8.0.4")
//    implementation("com.itextpdf.android:sign-android:8.0.4")

//    implementation("androidx.datastore:datastore:1.1.1")
//    implementation("androidx.datastore:datastore-rxjava2:1.1.1")
//    implementation("androidx.datastore:datastore-rxjava3:1.1.1")
//    implementation("androidx.datastore:datastore-core:1.1.1")

//    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
//    implementation("androidx.datastore:datastore-preferences-rxjava2:1.1.1")
//    implementation("androidx.datastore:datastore-preferences-rxjava3:1.1.1")
//    implementation("androidx.datastore:datastore-preferences-core:1.1.1")
//    implementation("androidx.datastore:datastore:1.1.1")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}