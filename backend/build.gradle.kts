plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.earth"
version = "0.1.0"
description = "Earth - realtime global event globe (backend)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Web / Validation
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Security / OAuth2 (Google login only) / JWT
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Redis - pub/sub messaging backbone for realtime event/chat broadcast
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Realtime transport (STOMP over WebSocket) for globe event feed + chat rooms
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // API docs
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")

    // 통합 테스트용 PostgreSQL/Redis. 테스트가 돌 때만 임시 컨테이너를 띄우고 끝나면 버린다.
    // 운영 스택과 포트가 겹치지 않고(무작위 할당), 테스트가 운영 데이터에 닿을 경로가 없다.
    // Testcontainers 2.x부터 모듈 아티팩트에 testcontainers- 접두사가 붙는다.
    // 1.x 문서에 나오는 org.testcontainers:postgresql 로는 해석되지 않는다.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// bootRun은 로컬 개발 전용 태스크다. 여기서만 local 프로파일을 켜서 개발용 로그인
// (com.earth.dev)이 붙게 한다. 운영 배포는 java -jar로 뜨므로 이 설정의 영향을 받지 않는다.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    args("--spring.profiles.active=local")
}
