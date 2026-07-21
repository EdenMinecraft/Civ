plugins {
    alias(libs.plugins.paper.userdev)
    alias(libs.plugins.runpaper)
}

version = "2.0.1"

dependencies {
    paperweight {
        paperDevBundle(libs.versions.paper)
    }

    compileOnly(libs.barapi)
    compileOnly(files("./libs/GSit-3.3.1.jar"))
}
