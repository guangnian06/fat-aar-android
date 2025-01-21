plugins {
    groovy
    `java-library`
    `maven-publish`
    signing
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<GroovyCompile> {
    groovyOptions.encoding = "UTF-8"
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

sourceSets {
    main {
        java {
            srcDirs("src/main/java")
        }
        groovy {
            srcDirs("src/main/groovy")
        }
        resources {
            srcDirs("src/main/resources")
        }
    }
}

apply(from = "./upload.gradle.kts")

buildscript {
    repositories {
        mavenCentral()
    }
}

repositories {
    mavenCentral()
    google()
    maven {
        url = uri("https://plugins.gradle.org/m2/")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(gradleApi())
    implementation(localGroovy())
    implementation("org.javassist:javassist:3.30.2-GA")
    implementation("com.android.tools.build:gradle:8.7.3")
    //noinspection GradleDependency
    implementation("com.android.tools:common:31.7.3")
    implementation("org.ow2.asm:asm-commons:9.7.1")
    implementation("org.dom4j:dom4j:2.1.4")
    implementation("com.github.johnrengelman:shadow:8.1.1")
}
