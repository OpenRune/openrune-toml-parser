import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("jvm") version "1.9.0"
    id("maven-publish")
}

val buildDirectory = System.getenv("HOSTING_DIRECTORY") ?: "D:\\OpenRune\\openrune-hosting"
val buildNumber = "1.1"
val publishedArtifacts = mapOf(
    "toml-annotations" to "toml-annotations",
    "konbini" to "toml-konbini",
    "toml-core" to "toml-core",
    "toml-rsconfig" to "toml-rsconfig"
)
val publishedPomDependencies = mapOf(
    "toml-annotations" to emptyList(),
    "konbini" to emptyList(),
    "toml-core" to listOf(
        Triple("dev.or2", "toml-annotations", buildNumber),
        Triple("dev.or2", "toml-konbini", buildNumber)
    ),
    "toml-rsconfig" to listOf(
        Triple("dev.or2", "toml-core", buildNumber)
    )
)

repositories {
    mavenCentral()
    maven("https://raw.githubusercontent.com/OpenRune/hosting/master")
    maven("https://jitpack.io")
}

group = "dev.or2"
version = buildNumber

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "idea")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")

    group = "dev.or2"
    version = buildNumber

    java.sourceCompatibility = JavaVersion.VERSION_11

    repositories {
        mavenCentral()
        maven("https://raw.githubusercontent.com/OpenRune/hosting/master")
        maven("https://jitpack.io")
    }

    dependencies {
        implementation("com.michael-bull.kotlin-inline-logger:kotlin-inline-logger:1.0.3")
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
        }
    }

    val sourcesJar by tasks.registering(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

    if (name in publishedArtifacts.keys) {
        publishing {
            publications {
                create<MavenPublication>("mavenJava") {
                    artifact(tasks.named("jar"))
                    artifact(sourcesJar.get())
                    artifactId = publishedArtifacts.getValue(project.name)

                    pom {
                        val publishedArtifactName = publishedArtifacts.getValue(project.name)
                        name.set("OpenRune - $publishedArtifactName")
                        description.set("Module $publishedArtifactName of the OpenRune project.")
                        url.set("https://github.com/OpenRune")

                        licenses {
                            license {
                                name.set("Apache-2.0")
                                url.set("https://opensource.org/licenses/Apache-2.0")
                            }
                        }

                        developers {
                            developer {
                                id.set("openrune")
                                name.set("OpenRune Team")
                                email.set("contact@openrune.dev")
                            }
                        }

                        scm {
                            connection.set("scm:git:git://github.com/OpenRune.git")
                            developerConnection.set("scm:git:ssh://github.com/OpenRune.git")
                            url.set("https://github.com/OpenRune")
                        }

                        withXml {
                            val dependenciesNode = asNode().appendNode("dependencies")
                            publishedPomDependencies[project.name].orEmpty().forEach { (groupId, artifactId, version) ->
                                val dependencyNode = dependenciesNode.appendNode("dependency")
                                dependencyNode.appendNode("groupId", groupId)
                                dependencyNode.appendNode("artifactId", artifactId)
                                dependencyNode.appendNode("version", version)
                                dependencyNode.appendNode("scope", "compile")
                            }
                        }
                    }
                }
            }

            repositories {
                maven {
                    url = uri(buildDirectory)
                }
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("all") {
            artifactId = "toml-all"

            pom {
                name.set("OpenRune - toml-all")
                description.set("Aggregate module including toml-core and toml-rsconfig dependencies.")
                url.set("https://github.com/OpenRune")

                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://opensource.org/licenses/Apache-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("openrune")
                        name.set("OpenRune Team")
                        email.set("contact@openrune.dev")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/OpenRune.git")
                    developerConnection.set("scm:git:ssh://github.com/OpenRune.git")
                    url.set("https://github.com/OpenRune")
                }

                withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")
                    listOf(
                        Triple("dev.or2", "toml-core", buildNumber),
                        Triple("dev.or2", "toml-rsconfig", buildNumber)
                    ).forEach { (groupId, artifactId, version) ->
                        val dependencyNode = dependenciesNode.appendNode("dependency")
                        dependencyNode.appendNode("groupId", groupId)
                        dependencyNode.appendNode("artifactId", artifactId)
                        dependencyNode.appendNode("version", version)
                        dependencyNode.appendNode("scope", "compile")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            url = uri(buildDirectory)
        }
    }
}

