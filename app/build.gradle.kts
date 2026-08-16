import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dingding.souti"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dingding.souti"
        minSdk = 26
        // ★ targetSdk 33 = Android 13 规则：完全不检查 MEDIA_PROJECTION runtime grant
        // targetSdk 34+ 会要求 MEDIA_PROJECTION 已被 runtime 授权才能启动 FGS mediaProjection
        // 模拟器冷启动后 runtime grants 丢失 → 必须先手动授权过 mediaProjection 才能启动服务
        targetSdk = 33
        versionCode = 5
        versionName = "1.1.0"
        // ★ ABI 包含主流架构（之前只生成 x86_64，鸿蒙 ARM64 手机装不上）
        //    arm64-v8a = 麒麟芯片（鸿蒙/现代安卓）
        //    armeabi-v7a = 旧安卓
        //    x86_64 = 模拟器/部分平板
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    val keystoreProperties = Properties()
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // CameraX 实时摄像头预览与分析
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    // ML Kit 中文 OCR（离线免费，阿里云镜像有此包）
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    // PDF 文本提取（PDFBox 安卓移植版，仅文字版 PDF）
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // .xls 旧版 Excel 解析（jxl 轻量库）
    implementation("net.sourceforge.jexcelapi:jxl:2.6.12")
}
