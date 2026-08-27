package com.earth.dev;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(DevSecurityConfig.class);

    /**
     * 인증 없이 임의 계정 토큰을 발급하는 통로가 열려 있다는 사실을 기동 로그에 크게 남긴다.
     * @Profile("local") 하나에만 기대면, 배포 설정에 SPRING_PROFILES_ACTIVE=local이 잘못
     * 들어갔을 때 아무도 눈치채지 못한 채 완전한 인증 우회가 노출된다.
     */
    @PostConstruct
    void warnLoudly() {
        log.warn("""

                ****************************************************************
                * 개발 전용 로그인(/api/dev/login)이 활성화되어 있습니다.        *
                * 인증 없이 임의 계정의 토큰이 발급됩니다.                       *
                * 운영 환경이라면 즉시 local 프로파일을 해제하십시오.            *
                ****************************************************************
                """);
    }

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
