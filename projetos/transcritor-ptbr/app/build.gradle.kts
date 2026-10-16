plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    useLibrary("android.test.mock")
    namespace = "br.com.pmcn.transcritor"
    compileSdk = 35
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
        versionCode = 7
        versionName = "0.6.0"
    }
}
