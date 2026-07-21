plugins {
    alias(libs.plugins.paper.userdev)
}

version = "3.0.1"

dependencies {
    paperweight {
        paperDevBundle(libs.versions.paper)
    }

    compileOnly(project(":plugins:civmodcore-paper"))
    compileOnly(project(":plugins:namelayer-paper"))
    compileOnly(project(":plugins:citadel-paper"))

    testImplementation(libs.bundles.junit)
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation(project(":plugins:civmodcore-paper"))
    testImplementation(project(":plugins:namelayer-paper"))
    testImplementation(project(":plugins:citadel-paper"))
}
