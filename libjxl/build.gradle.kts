plugins {
    id("com.android.library")
    id("maven-publish")
}

android {
    namespace = "fr.oupson.libjxl"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34

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
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

publishing {

publications {
        create<MavenPublication>("relocation") {
            pom {
                groupId = "fr.oupson"
                artifactId = "libjxl"
                version = "0.4.0"

                afterEvaluate {
                    from(components["release"])
                }
            }
        }
    }
}