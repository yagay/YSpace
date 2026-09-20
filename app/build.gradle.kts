plugins {
    id("com.android.application")
}

android {
    namespace = "com.yagay.YSpace"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yagay.YSpace"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
}
