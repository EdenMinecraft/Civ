plugins {
    id("java")
}

group = "world.edenmc"
version = "1.0.0"

dependencies {
    api("com.google.code.gson:gson:2.11.0")
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.junit)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
