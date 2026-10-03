import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.20"
    id("com.google.devtools.ksp") version "2.1.20-2.0.0"
}

repositories {
    maven("https://maven.teamresourceful.com/repository/maven-public/")
    maven("https://libraries.minecraft.net")
    mavenCentral()
}

dependencies {
    compileOnly(ksp(project(":processor"))!!)

    implementation("com.mojang:datafixerupper:10.0.21")

    implementation("org.jetbrains:annotations:26.0.2")
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