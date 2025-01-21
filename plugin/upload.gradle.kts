import java.util.Properties
import java.io.File
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSet

val PUBLISH_GROUP_ID = "com.kezong"
val PUBLISH_VERSION = "1.3.9"

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from("src/main/java", "src/main/groovy", "src/main/resources")
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.named("javadoc"))
}

val properties = Properties().apply {
    val localProperties = rootProject.file("local.properties")
    if (localProperties.exists()) {
        load(localProperties.inputStream())
    }
}

afterEvaluate {
    configure<PublishingExtension> {
    publications {
        register<MavenPublication>("release") {
            groupId = PUBLISH_GROUP_ID
            artifactId = "$PUBLISH_GROUP_ID.gradle.plugin"
            version = PUBLISH_VERSION

            from(components["java"])
            artifact(sourcesJar.get())
            artifact(javadocJar.get())

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
            }
        }
    }
    
    repositories {
        maven {
            url = if (PUBLISH_VERSION.endsWith("-SNAPSHOT")) {
                uri(properties["mavenSnapshotUrl"]?.toString() ?: "")
            } else {
                uri(properties["mavenReleaseUrl"]?.toString() ?: "")
            }
            isAllowInsecureProtocol = true
            credentials {
                username = properties["mavenUsername"]?.toString()
                password = properties["mavenPassword"]?.toString()
            }
        }
    }
    }
}
