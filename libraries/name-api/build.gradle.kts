plugins {
    id("java")
}

group = "net.civmc"
version = "1.0.0"

dependencies {
    api(libs.hikaricp)
    api(libs.configurate.yaml)
    api(libs.mariadb.client)
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.junit)
    testImplementation("org.mockito:mockito-core:5.11.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
