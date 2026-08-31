package com.earth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS 허용 오리진.
 *
 * <p>목록인 이유는 개발 중 한 서버를 여러 주소로 접근하기 때문이다. 노트북에서는
 * localhost로, 폰에서 실제 GPS 동작을 확인할 때는 같은 네트워크의 LAN IP로 접속하는데
 * 둘은 서로 다른 오리진이라 하나만 허용하면 나머지가 CORS에 막힌다.
 *
 * <p>쉼표로 구분해 지정한다: {@code FRONTEND_ORIGIN=http://localhost:5173,http://192.168.0.19:5173}
 */
@ConfigurationProperties(prefix = "earth.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
