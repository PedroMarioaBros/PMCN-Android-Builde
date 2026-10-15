plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "br.com.pmcn.transcritor"
    compileSdk = 35
    ndkVersion = "27.2.12479018"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    defaultConfig {
        applicationId = "br.com.pmcn.transcritor"
        minSdk = 26
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
        targetSdk = 28
        versionCode = 3
        versionName = "0.3.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
}
