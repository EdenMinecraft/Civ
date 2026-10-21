plugins {
    alias(libs.plugins.paper.userdev)
    alias(libs.plugins.shadow)
}

version = "3.0.6"

dependencies {
    paperweight {
        paperDevBundle(libs.versions.paper)
    }

    compileOnly(project(":plugins:civmodcore-paper"))
    api(project(":libraries:name-api"))

    testImplementation(libs.bundles.junit)
    testImplementation(libs.mockbukkit)
    testImplementation("io.papermc.paper:paper-api:${libs.versions.paper.get()}")
    testImplementation("org.mockito:mockito-core:5.14.2")
    // GroupManagerDao references civmodcore's ManagedDatasource, which Mockito must load to mock it.
    testImplementation(project(":plugins:civmodcore-paper"))
    // Real-MariaDB DAO tests: Testcontainers boots a throwaway MariaDB so namelayer's migrations run
    // against the actual DBMS instead of a mock.
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.mariadb.driver)
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

tasks.withType<Test> {
    // Testcontainers can't auto-detect the Docker socket when the active context is colima (the
    // /var/run/docker.sock symlink is dead). Point it at the colima socket unless the env already
    // sets DOCKER_HOST (e.g. CI with its own daemon).
    if (System.getenv("DOCKER_HOST") == null) {
        val colimaSocket = file("${System.getProperty("user.home")}/.colima/default/docker.sock")
        if (colimaSocket.exists()) {
            environment("DOCKER_HOST", "unix://${colimaSocket.absolutePath}")
            // Ryuk bind-mounts the docker socket; the host path doesn't exist inside the colima VM,
            // so tell Testcontainers the in-VM socket path instead.
            environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
        }
    }
    // docker-java negotiates API 1.32 by default, which modern Docker Engine (1.44+) rejects.
    // docker-java reads the "api.version" system property before the DOCKER_API_VERSION env var.
    if (System.getenv("DOCKER_API_VERSION") == null) {
        environment("DOCKER_API_VERSION", "1.44")
        systemProperty("api.version", "1.44")
    }
    // slf4j-simple backs Testcontainers' logging; keep it at WARN so container output stays quiet.
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn")
}

// https://docs.mockbukkit.org/docs/en/user_guide/advanced/paperweight
paperweight {
    addServerDependencyTo = configurations.named(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).map { setOf(it) }
}
