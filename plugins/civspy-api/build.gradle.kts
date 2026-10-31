version = "2.0.1"

dependencies {
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.postgresql:postgresql:42.7.14")

    testImplementation(libs.bundles.junit)
    testImplementation(libs.mockito.core)
}
