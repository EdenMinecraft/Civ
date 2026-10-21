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
    compileOnly(project(":plugins:simpleadminhacks-paper"))
    compileOnly(project(":plugins:combattagplus-paper"))
    // InvSee++'s maven repo requires auth, so compile against the exact jar we deploy
    compileOnly(files(rootProject.file("ansible/src/paper-plugins/InvSee++.jar")))
    //api(project(":libraries:name-api"))
}
