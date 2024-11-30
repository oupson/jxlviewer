import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
    alias(libs.plugins.vanniktech.maven.publish)
}

android {
    namespace = "fr.oupson.libjxl"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
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
        multipleVariants {
            withSourcesJar()
        }
    }
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}

fun MavenPom.configure() {
    name.set("libjxl")
    description.set("A JPEG-XL decoding library for android.")
    url.set("https://github.com/oupson/jxlviewer/")
    version = libs.versions.release.version.get()
    licenses {
        license {
            name.set("MIT License")
            url.set("https://mit-license.org/")
            distribution.set("https://mit-license.org/")
        }
    }
    developers {
        developer {
            id.set("oupson")
            name.set("oupson")
            url.set("https://github.com/oupson/")
        }
    }
    scm {
        url.set("https://github.com/oupson/jxlviewer/")
        connection.set("scm:git:git://github.com/oupson/jxlviewer.git")
        developerConnection.set("scm:git:https://github.com/oupson/jxlviewer.git")
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates("fr.oupson", "libjxl", libs.versions.release.version.get())
    pom {
        this.configure()
    }
}