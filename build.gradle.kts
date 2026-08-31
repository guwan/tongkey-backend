plugins {
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    `java`
}

group = "com.tongkey"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}

// 构建产物名称（替代 Maven finalName）
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("tongkey-server.jar")
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // 自身数据库
    runtimeOnly("org.postgresql:postgresql")

    // 第三方数据源 JDBC 驱动
    implementation("com.mysql:mysql-connector-j")
    implementation("org.mariadb.jdbc:mariadb-java-client")
    implementation("com.oracle.database.jdbc:ojdbc11")
    implementation("com.microsoft.sqlserver:mssql-jdbc")

    // API 文档
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
