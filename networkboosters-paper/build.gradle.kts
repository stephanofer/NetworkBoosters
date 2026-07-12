plugins {
    java
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":networkboosters-api"))

    compileOnly(libs.paper.api)
    compileOnly(libs.network.player.settings)
    compileOnly(libs.placeholder.api)
    compileOnly(libs.zmenu.api)

    implementation(libs.craftkit.database)
    implementation(libs.craftkit.redis)
    implementation(libs.craftkit.zmenu)
    implementation(libs.boosted.yaml)
    implementation(libs.cloud.paper)
    implementation(libs.cloud.minecraft.extras)
}

tasks {
    processResources {
        val pluginVersion = project.version.toString()
        inputs.property("version", pluginVersion)

        filesMatching("paper-plugin.yml") {
            expand("version" to pluginVersion)
        }
    }

    jar {
        enabled = false
    }

    shadowJar {
        archiveClassifier = ""
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
        append("META-INF/io.netty.versions.properties")

        exclude("LICENSE")
        exclude("META-INF/LICENSE")
        exclude("META-INF/LICENSE.txt")
        exclude("META-INF/NOTICE")

        relocate("com.hera.craftkit", "com.stephanofer.networkboosters.libs.craftkit")
        relocate("com.zaxxer", "com.stephanofer.networkboosters.libs.hikari")
        relocate("org.flywaydb", "com.stephanofer.networkboosters.libs.flyway")
        relocate("tools.jackson", "com.stephanofer.networkboosters.libs.jackson3")
        relocate("com.fasterxml.jackson", "com.stephanofer.networkboosters.libs.jackson")
        relocate("com.mysql", "com.stephanofer.networkboosters.libs.mysql")
        relocate("com.google.protobuf", "com.stephanofer.networkboosters.libs.protobuf")
        relocate("io.lettuce", "com.stephanofer.networkboosters.libs.lettuce")
        relocate("redis.clients.authentication", "com.stephanofer.networkboosters.libs.redisAuthx")
        relocate("io.netty", "com.stephanofer.networkboosters.libs.netty")
        relocate("reactor", "com.stephanofer.networkboosters.libs.reactor")
        relocate("org.reactivestreams", "com.stephanofer.networkboosters.libs.reactiveStreams")
        relocate("dev.dejvokep.boostedyaml", "com.stephanofer.networkboosters.libs.boostedyaml")
        relocate("org.incendo.cloud", "com.stephanofer.networkboosters.libs.cloud")
        relocate("io.leangen.geantyref", "com.stephanofer.networkboosters.libs.geantyref")
    }

    assemble {
        dependsOn(shadowJar)
    }
}
