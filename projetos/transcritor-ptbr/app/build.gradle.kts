plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "br.com.pmcn.transcritor"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.pmcn.transcritor"
        minSdk = 26
        targetSdk = 28
        versionCode = 2
        versionName = "0.2.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
}
