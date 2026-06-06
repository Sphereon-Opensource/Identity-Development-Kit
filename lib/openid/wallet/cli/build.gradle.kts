plugins {
    alias(sphereonplug.plugins.org.jetbrains.kotlin.jvm)
    alias(sphereonplug.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(sphereonplug.plugins.dev.zacsweers.metro)
    application
}

metro {
}

group = "com.sphereon.wallet"

application {
    mainClass.set("com.sphereon.openid.wallet.cli.WalletApplicationKt")
}

dependencies {
    implementation(projects.libWalletPublic)
    implementation(projects.libWalletImpl)
    implementation(projects.libCoreApiPublic)
    implementation(projects.libCoreApiDefault)
    implementation(sphereonlib.io.ktor.server.core)
    implementation(sphereonlib.io.ktor.server.cio)
    implementation(sphereonlib.org.jetbrains.kotlinx.coroutines.core)
    implementation(sphereonlib.org.jetbrains.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(sphereonlib.org.jetbrains.kotlinx.coroutines.test)
    testImplementation(sphereonlib.io.ktor.client.cio)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
