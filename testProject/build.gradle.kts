import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.0"
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://libraries.minecraft.net")
}

dependencies {
    compileOnly(ksp(project(":processor"))!!)

    implementation("com.mojang:datafixerupper:8.0.16")
}

ksp {
    arg("meowdding.codecs.project_name", project.name)
    arg("meowdding.codecs.package", "me.owdding.ktcodecs.generated")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}