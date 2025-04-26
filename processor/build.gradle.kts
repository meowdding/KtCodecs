plugins {
    kotlin("jvm") version "2.1.20"
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.google.devtools.ksp:symbol-processing-api:2.0.20-1.0.25")

    implementation("com.squareup:kotlinpoet:1.13.0")
    implementation("com.squareup:kotlinpoet-ksp:1.13.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "KtCodecs"
            from(components["java"])

            pom {
                name.set("KtCodecs")
                url.set("https://github.com/meowdding/ktcodecs")

                scm {
                    connection.set("git:https://github.com/meowdding/ktcodecs.git")
                    developerConnection.set("git:https://github.com/meowdding/ktcodecs.git")
                    url.set("https://github.com/meowdding/ktcodecs")
                }
            }
        }
    }
    repositories {
        maven {
            setUrl("https://maven.teamresourceful.com/repository/thatgravyboat/")
            credentials {
                username = System.getenv("MAVEN_USER") ?: providers.gradleProperty("maven_username").orNull
                password = System.getenv("MAVEN_PASS") ?: providers.gradleProperty("maven_password").orNull
            }
        }
    }
}