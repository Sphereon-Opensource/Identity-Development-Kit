plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.jvm)
    id("me.champeau.jmh") version "0.7.3"
}

dependencies {
    implementation(project(":lib-core-api-public"))
    implementation(project(":lib-core-api-default"))

    jmh(project(":lib-core-api-public"))
    jmh(project(":lib-core-api-default"))
}

jmh {
    warmupIterations = 3
    iterations = 5
    fork = 1
    benchmarkMode = listOf("avgt")
    timeUnit = "ns"
    profilers = listOf("gc")
    resultFormat = "JSON"
}

tasks.withType<PublishToMavenRepository>().configureEach {
    enabled = false
}

tasks.withType<PublishToMavenLocal>().configureEach {
    enabled = false
}

tasks.matching {
    it.name == "publish" ||
        it.name.startsWith("publishAllPublicationsTo") ||
        it.name.startsWith("publishPluginMavenPublicationTo")
}.configureEach {
    enabled = false
}

tasks.matching { it.name.startsWith("sign") }.configureEach {
    enabled = false
}
