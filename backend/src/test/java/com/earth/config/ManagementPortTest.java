package com.earth.config;

import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공개 포트(Cloudflare Tunnel이 여는 8080)와 관리 포트(docker 내부망의 Prometheus만 닿는 8081)의 경계.
 * 수치가 공개 포트로 새거나, health가 공개 포트에서 사라지면(헬스체크·외부 업타임 체크가 깨짐) 실패한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class ManagementPortTest extends IntegrationTest {

    @LocalServerPort
    int serverPort;

    @LocalManagementPort
    int managementPort;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void 공개_포트에서는_health만_열리고_수치는_닫힌다() throws Exception {
        HttpResponse<String> health = get(serverPort, "/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"UP\"");

        assertThat(get(serverPort, "/actuator/prometheus").statusCode()).isEqualTo(404);
    }

    @Test
    void 관리_포트에서는_Prometheus_수치가_열린다() throws Exception {
        HttpResponse<String> metrics = get(managementPort, "/actuator/prometheus");
        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body())
                .contains("jvm_memory_used_bytes")
                .contains("hikaricp_connections_active")
                .contains("http_server_requests_seconds_bucket");
    }
}
