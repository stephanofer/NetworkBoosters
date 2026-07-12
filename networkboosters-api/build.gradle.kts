import org.gradle.jvm.tasks.Jar

plugins {
    `java-library`
    `maven-publish`
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Jar>().configureEach {
    destinationDirectory = rootProject.layout.projectDirectory.dir("target-api")
}

dependencies {
    compileOnly(libs.paper.api)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("api") {
            from(components["java"])
            artifactId = "networkboosters-api"
        }
    }
}
