plugins {
    alias(libs.plugins.paper.userdev)
}

version = "1.0.1"

dependencies {
    paperweight {
        paperDevBundle(libs.versions.paper)
    }

    compileOnly(project(":plugins:civmodcore-paper"))
    compileOnly(project(":plugins:civchat2-paper"))
    compileOnly(project(":plugins:jukealert-paper"))
    compileOnly(project(":plugins:namelayer-paper"))
    compileOnly(libs.luckperms.api)
    compileOnly(libs.bundles.discordsrv)
    //compileOnly("com.discordsrv:discordsrv:1.28.0")

}
