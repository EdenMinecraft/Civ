plugins {
    alias(libs.plugins.paper.userdev)
}

version = "1.0.0"

dependencies {
    paperweight {
        paperDevBundle(libs.versions.paper)
    }

    compileOnly(project(":plugins:civmodcore-paper"))
    compileOnly(project(":plugins:citadel-paper"))
    compileOnly(project(":plugins:namelayer-paper"))
    compileOnly(project(":plugins:bastion-paper"))
    compileOnly(project(":plugins:exilepearl-paper"))
}
