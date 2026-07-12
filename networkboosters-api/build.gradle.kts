plugins {
    `java-library`
    `maven-publish`
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    compileOnly(libs.paper.api)
}

publishing {
    publications {
        create<MavenPublication>("api") {
            from(components["java"])
            artifactId = "networkboosters-api"
        }
    }
}
