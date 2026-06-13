plugins {
    alias(libs.plugins.paper.userdev)
}

version = "3.1.0"

dependencies {
    paperweight {
        paperDevBundle(libs.versions.paper)
    }

    compileOnly(project(":plugins:civmodcore-paper"))
    compileOnly(project(":plugins:namelayer-paper"))
    compileOnly(project(":plugins:citadel-paper"))
    compileOnly(project(":plugins:heliodor-paper"))

    testImplementation(project(":plugins:civmodcore-paper"))
    testImplementation(project(":plugins:namelayer-paper"))
    testImplementation(project(":plugins:citadel-paper"))
    testImplementation(project(":plugins:heliodor-paper"))
    testImplementation(libs.bundles.junit)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.paper.api)
    testImplementation("org.mockito:mockito-core:5.11.0")
}

// MockBukkit supplies the test server and registry implementation.
paperweight {
    addServerDependencyTo = configurations.named(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).map { setOf(it) }
}
