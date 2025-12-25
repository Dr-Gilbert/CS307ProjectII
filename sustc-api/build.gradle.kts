import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    // 【关键】移除了 spring.boot 插件，防止打成 executable jar
    alias(libs.plugins.spring.dependencyManagement)
    alias(libs.plugins.lombok)
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()
}


group = "io.sustc"
version = "1.0.0"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:2.7.16")
    }
}

dependencies {
    // 依然引用 Spring Boot Starter，但作为普通库引用
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-web")

    implementation("com.opencsv:opencsv:5.7.1")
    implementation("org.furyio:fury-core:0.3.1")
}

// 下面是提交作业用的打包任务，保持不变
tasks.register("submitJar") {
    group = "application"
    description = "Prepare an uber-JAR for submission"

    // 使用 ShadowJar 打包所有依赖，但不包含 Spring Boot 的启动结构
    tasks.getByName<ShadowJar>("shadowJar") {
        archiveFileName.set("sustc-api.jar")
        destinationDirectory.set(file("$rootDir/submit"))
        dependencies {
            exclude(dependency("ch.qos.logback:logback-.*"))
        }
        // 确保包含 classifier 为空的 jar
        archiveClassifier.set("")
    }.let { dependsOn(it) }
}

tasks.clean {
    delete(fileTree("$rootDir/submit").matching { include("*.jar") })
}

// 强制启用 jar 任务，并确保它不为空
tasks.jar {
    enabled = true
    archiveClassifier.set("") // 确保生成的是 sustc-api.jar 而不是 sustc-api-plain.jar
}