version = "2.0.1"

dependencies {
    implementation("com.zaxxer:HikariCP:3.4.2")
    implementation("org.postgresql:postgresql:42.3.5")

    testImplementation(libs.bundles.junit)
    testImplementation("org.mockito:mockito-core:5.11.0")
}
