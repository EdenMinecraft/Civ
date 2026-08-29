plugins {
    alias(libs.plugins.paper.userdev)
    alias(libs.plugins.shadow)
}

version = "3.0.0"

dependencies {
    paperweight {
        paperDevBundle(libs.versions.paper)
    }

    compileOnly(project(":plugins:civmodcore-paper"))
    compileOnly(project(":plugins:namelayer-paper"))

    implementation(libs.ipaddress)
    implementation(libs.jsoup)
    implementation(libs.redis.jedis)
    implementation(project(":libraries:banstick-api"))
}
