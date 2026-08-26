package com.earth.dev;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 개발 전용 로그인 경로만 열어주는 별도 필터 체인.
 *
 * <p>메인 SecurityConfig를 건드리지 않고 local 프로파일에서 통째로 붙었다 떨어지도록 분리했다.
 * 운영에도 배포되는 설정 파일에 /api/dev 예외 규칙이 남아 있는 것 자체가 위험 요소이기 때문이다.
 */
@Configuration
@Profile("local")
public class DevSecurityConfig {

    @Bean
    @Order(1) // @Order가 없는 메인 체인(최하위 우선순위)보다 먼저 평가되어야 한다
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/dev/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
