plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.yapi.plugin"
version = "1.0.0"

repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create("IC", "2024.3")
        bundledPlugin("com.intellij.java")
    }
    implementation("com.google.code.gson:gson:2.10.1")
}

intellijPlatform {
    pluginConfiguration {
        name = "YApi Doc Generator"
        vendor {
            name = "Cheney"
            email = "socbb.cn@gmail.com"
            url = "https://github.com/socbb/idea-plugin-yapi.git"
        }
        ideaVersion {
            sinceBuild = "232"   // 2023.2+
            untilBuild = provider { null }
        }
    }

    // 发布配置 - 上传到 JetBrains Marketplace
    publishing {
        // 从环境变量或 gradle.properties 读取 token
        // 设置方式: ORG_GRADLE_PROJECT_jetbrainsToken=your_token_here
        token = providers.environmentVariable("JETBRAINS_TOKEN")
            .orElse(providers.gradleProperty("jetbrainsToken"))
    }

    // 签名配置（可选，建议开启）
    // signPlugin { certificateChain = ""; privateKey = "" }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.encoding = "UTF-8"
}
