package com.earth.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 PostgreSQL과 Redis를 띄워 돌리는 통합 테스트의 공통 기반.
 *
 * <p>DB를 실제로 띄우는 이유는 검증 대상이 <b>동시성</b>이기 때문이다. 인메모리 DB나 목으로는
 * 트랜잭션 격리와 행 잠금이 재현되지 않아, 통과해도 아무것도 증명하지 못한다. Flyway
 * 마이그레이션이 실제로 적용되는지까지 함께 검증된다.
 *
 * <p>운영 스택이 같은 호스트에서 돌기 때문에 고정 포트를 쓰는 개발용 DB를 재사용할 수 없다.
 * Testcontainers는 컨테이너마다 무작위 포트를 할당하고 Spring이 그 값을 주입받으므로 포트
 * 충돌이 구조적으로 발생하지 않는다. 테스트가 DB를 비워도 운영 데이터에 닿을 경로가 없다는
 * 점이 더 중요하다.
 *
 * <p><b>컨테이너를 정적 초기화에서 직접 띄우고 JUnit 생명주기에 맡기지 않는다.</b>
 * {@code @Testcontainers} + {@code @Container}는 테스트 클래스마다 컨테이너를 재시작하는데,
 * Spring 컨텍스트는 설정이 같으면 클래스 사이에 캐시된다. 그래서 두 번째 클래스에서 컨테이너가
 * 새 포트로 다시 뜨면 캐시된 컨텍스트는 죽은 포트를 계속 가리키고,
 * {@code Could not open JPA EntityManager for transaction}으로 실패한다.
 * 이 방식은 컨테이너를 한 번만 띄워 그 문제를 없애고 전체 실행 시간도 줄인다.
 * 정리는 Testcontainers의 Ryuk 컨테이너가 JVM 종료 시 맡는다.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    // GenericContainer에는 전용 컨테이너 타입이 없으므로 어떤 서비스인지 이름으로 알려준다.
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }
}
