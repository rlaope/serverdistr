plugins {
    kotlin("jvm") version "1.9.25" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
    kotlin("plugin.jpa") version "1.9.25" apply false
    id("org.springframework.boot") version "3.2.5" apply false
    id("io.spring.dependency-management") version "1.1.5" apply false
}

allprojects {
    group = "com.khope"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    dependencies {
        "implementation"("org.springframework.boot:spring-boot-starter-actuator")
        "runtimeOnly"("io.micrometer:micrometer-registry-prometheus")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "17"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
        workingDir = rootProject.projectDir
    }
}