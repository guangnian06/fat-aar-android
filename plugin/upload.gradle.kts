plugins {
    `maven-publish`
    java
    signing
}

val sourcesJar by tasks.creating(Jar::class) {
    from(sourceSets["main"].allJava)
    archiveClassifier.set("sources")
}

val javadocJar by tasks.creating(Jar::class) {
    from(tasks["javadoc"])
    archiveClassifier.set("javadoc")
}

val properties = Properties().apply {
    val localProperties = rootProject.file("local.properties")
    if (localProperties.exists()) {
        localProperties.inputStream().use { load(it) }
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = PUBLISH_GROUP_ID
            artifactId = "$PUBLISH_GROUP_ID.gradle.plugin"
            version = PUBLISH_VERSION

            artifact(tasks["jar"])
            artifact(sourcesJar)
            artifact(javadocJar)

            pom {
                name.set("$PUBLISH_GROUP_ID.gradle.plugin")
                description.set("A gradle plugin that merge dependencies into the final aar file works with AGP 3.+")
                url.set("https://github.com/kezong/fat-aar-android")
                
                licenses {
                    license {
                        name.set("The MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("kezong")
                        name.set("kezong")
                        email.set("kezong1811@gmail.com")
                    }
                }
                
                scm {
                    connection.set("scm:git:github.com/kezong/fat-aar-android.git")
                    developerConnection.set("scm:git:ssh://github.com/kezong/fat-aar-android.git")
                    url.set("https://github.com/kezong/fat-aar-android/tree/master")
                }

                // Include transitive dependencies in POM
                withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")

                    project.configurations["implementation"].allDependencies.forEach { dependency ->
                        if (dependency.group == "com.android.tools.build" && dependency.name == "gradle") {
                            return@forEach
                        }
                        if (dependency.group == null) {
                            return@forEach
                        }
                        val dependencyNode = dependenciesNode.appendNode("dependency")
                        dependencyNode.appendNode("groupId", dependency.group)
                        dependencyNode.appendNode("artifactId", dependency.name)
                        dependencyNode.appendNode("version", dependency.version)
                    }
                }
            }
        }
    }
    
    repositories {
        maven {
            url = uri(if (PUBLISH_VERSION.endsWith("-SNAPSHOT")) 
                properties["mavenSnapshotUrl"].toString()
            else 
                properties["mavenReleaseUrl"].toString()
            )
            isAllowInsecureProtocol = true
            credentials {
                username = properties.getProperty("mavenUsername")
                password = properties.getProperty("mavenPassword")
            }
        }
    }
}
