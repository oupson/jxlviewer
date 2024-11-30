plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

android {
    namespace = "fr.oupson.libjxl"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles ("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++11")
                targets("jxlreader")
                arguments("-DANDROID_ARM_NEON=ON")
            }
        }

        splits {
            abi {
                isEnable = true
                reset()
                include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
                isUniversalApk = true
            }
        }
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
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}

publishing {

publications {
        create<MavenPublication>("relocation") {
            pom {
                groupId = "fr.oupson"
                artifactId = "libjxl"
                version = libs.versions.release.version.get()

                afterEvaluate {
                    from(components["release"])
                }
            }
        }
    }
}