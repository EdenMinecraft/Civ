plugins {
    alias(libs.plugins.paper.userdev)
}

version = "1.0.0"

repositories {
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    paperweight {
        paperDevBundle(libs.versions.paper)
    }

    compileOnly(project(":plugins:civmodcore-paper"))
    compileOnly(project(":plugins:namelayer-paper"))
    compileOnly(project(":plugins:citadel-paper"))
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
}
