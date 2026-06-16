plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.shortly"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot WebFlux (reactive)
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // R2DBC (reactive PostgreSQL)
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.postgresql:r2dbc-postgresql:1.0.5.RELEASE")

    // Redis Reactive
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // Flyway (migrations) — still uses JDBC (blocking), separate connection
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql") // JDBC driver for Flyway

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Actuator (health checks, metrics)
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // External libs
    implementation("com.maxmind.geoip2:geoip2:4.2.1")
    implementation("com.github.ua-parser:uap-java:1.5.4")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.testcontainers:testcontainers:1.20.2")
    testImplementation("org.testcontainers:junit-jupiter:1.20.2")
    testImplementation("org.testcontainers:postgresql:1.20.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

springBoot {
    buildInfo()
}
