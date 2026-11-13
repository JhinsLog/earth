package com.earth.controller;

import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 공개 포트(8080)의 {@code /actuator/health}.
 *
 * <p>수치를 공개 포트에서 빼려고 관리 포트를 8081로 나누면 actuator 전체가 옮겨 가 이 경로가 8080에서 사라진다.
 * 그런데 compose 헬스체크, {@code earth-ctl health}, 외부 업타임 체크가 모두 8080의 이 경로를 본다.
 * Spring의 {@code additional-path}로 health를 남기려 했지만 경로를 한 단계({@code /healthz})만 허용해
 * 기존 경로를 지킬 수 없었다. 그래서 같은 판정(HealthEndpoint)을 이 경로로 다시 내보낸다.
 *
 * <p>상태만 준다. 세부 항목(DB·Redis 연결 상태)은 공개 포트에 내놓지 않는다.
 */
@RestController
public class PublicHealthController {

    private final HealthEndpoint healthEndpoint;

    public PublicHealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/actuator/health")
    public ResponseEntity<Map<String, String>> health() {
        Status status = healthEndpoint.health().getStatus();
        // 헬스체크는 응답 코드로 판정하는 도구가 많다. DOWN을 200으로 주면 죽은 서버가 살아 보인다(actuator와 같은 503).
        HttpStatus code = Status.UP.equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(code).body(Map.of("status", status.getCode()));
    }
}
