plugins {
    alias(libs.plugins.shadow)
}

version = "1.0.0"

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")

    api(libs.hikaricp)
    api(libs.configurate.yaml)
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.6")
    implementation(libs.redis.jedis)

    api(project(":libraries:banstick-api"))
}
