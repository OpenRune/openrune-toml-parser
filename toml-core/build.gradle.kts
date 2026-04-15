import java.nio.file.Paths
import kotlin.collections.listOf
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.inputStream
import org.gradle.api.tasks.testing.Test

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":toml-annotations"))
    implementation(kotlin("reflect"))
    implementation(project(":konbini"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}