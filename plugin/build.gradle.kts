import org.jreleaser.model.Active

plugins {
    groovy
    `java-library`
    `maven-publish`
    id("org.jreleaser") version "1.15.0"
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

group = "io.github.ponyets.fataar"
version = "1.5.1"

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from("src/main/java", "src/main/groovy", "src/main/resources")
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.named("javadoc"))
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = group.toString()
            artifactId = "$group.gradle.plugin"
            version = version

            from(components["java"])
            artifact(sourcesJar.get())
            artifact(javadocJar.get())

            pom {
                name = "Fat AAR Android Gradle Plugin"
                description =
                    "A gradle plugin that merge and shadow dependencies into the final aar file works with AGP 3~8.7.3"
                url = "https://github.com/guangnian06/fat-aar-android"

                licenses {
                    license {
                        name = "The MIT License"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }

                developers {
                    developer {
                        id = "kezong"
                        name = "kezong"
                        email = "kezong1811@gmail.com"
                    }
                    developer {
                        id = "ponyets"
                        name = "Mingwei Pan"
                        email = "ponyets@outlook.com"
                    }
                }

                scm {
                    connection = "scm:git:github.com/guangnian06/fat-aar-android.git"
                    developerConnection = "scm:git:ssh://github.com/guangnian06/fat-aar-android.git"
                    url = "https://github.com/guangnian06/fat-aar-android/tree/master"
                }
            }
        }
    }

    repositories {
        maven(layout.buildDirectory.dir("staging-deploy"))
    }
}

jreleaser {
    gitRootSearch = true

    signing {
        active = Active.ALWAYS
        armored = true
    }
    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    active = Active.ALWAYS
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository("build/staging-deploy")
                }
            }
        }
    }
}

