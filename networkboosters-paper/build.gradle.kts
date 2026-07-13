import java.util.zip.ZipFile
import org.gradle.api.tasks.Internal
import org.gradle.process.CommandLineArgumentProvider

val mockitoAgent = configurations.register("mockitoAgent")

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

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.paper.api)
    testRuntimeOnly(libs.junit.platform.launcher)

    mockitoAgent(libs.mockito.core) {
        isTransitive = false
    }
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
        destinationDirectory = rootProject.layout.projectDirectory.dir("target")
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
        relocate("org.snakeyaml.engine", "com.stephanofer.networkboosters.libs.snakeyamlEngine")
        relocate("org.incendo.cloud", "com.stephanofer.networkboosters.libs.cloud")
        relocate("io.leangen.geantyref", "com.stephanofer.networkboosters.libs.geantyref")
    }

    test {
        useJUnitPlatform()

        val mockitoAgentPath = mockitoAgent.map { it.asPath }
        jvmArgumentProviders.add(object : CommandLineArgumentProvider {
            @get:Internal
            val javaAgent = mockitoAgentPath

            override fun asArguments(): Iterable<String> = listOf("-javaagent:${javaAgent.get()}")
        })
    }

    val shadowJarTask = named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar")
    val shadowArchiveFile = shadowJarTask.flatMap { it.archiveFile }

    val verifyShadowJar by registering {
        dependsOn(shadowJarTask)
        inputs.file(shadowArchiveFile)

        doLast {
            val jarFile = shadowArchiveFile.get().asFile
            val entries = ZipFile(jarFile).use { zip ->
                zip.entries().asSequence().map { it.name }.toSet()
            }

            val requiredEntries = listOf(
                "paper-plugin.yml",
                "com/stephanofer/networkboosters/NetworkBoostersPlugin.class",
                "com/stephanofer/networkboosters/NetworkBoostersBootstrap.class",
                "com/stephanofer/networkboosters/NetworkBoostersLoader.class"
            )

            val missingEntries = requiredEntries.filterNot(entries::contains)
            check(missingEntries.isEmpty()) {
                "Shadow JAR is missing required plugin entries: ${missingEntries.joinToString()}"
            }

            val forbiddenPrefixes = listOf(
                "com/hera/craftkit/",
                "com/zaxxer/",
                "org/flywaydb/",
                "tools/jackson/",
                "com/fasterxml/jackson/",
                "com/mysql/",
                "com/google/protobuf/",
                "io/lettuce/",
                "redis/clients/authentication/",
                "io/netty/",
                "reactor/",
                "org/reactivestreams/",
                "dev/dejvokep/boostedyaml/",
                "org/snakeyaml/engine/",
                "org/incendo/cloud/",
                "io/leangen/geantyref/",
                "com/stephanofer/networkplayersettings/",
                "fr/maxlego08/menu/",
                "me/clip/placeholderapi/"
            )

            val leakedEntries = entries.filter { entry ->
                forbiddenPrefixes.any(entry::startsWith)
            }

            check(leakedEntries.isEmpty()) {
                "Shadow JAR contains unrelocated or external API entries: ${leakedEntries.take(20).joinToString()}"
            }
        }
    }

    assemble {
        dependsOn(shadowJar)
        dependsOn(verifyShadowJar)
    }
}
