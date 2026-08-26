package com.earth.config;

import com.earth.security.jwt.JwtAuthenticationFilter;
import com.earth.security.oauth2.CustomOAuth2UserService;
import com.earth.security.oauth2.OAuth2SuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsProperties corsProperties;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                           OAuth2SuccessHandler oAuth2SuccessHandler,
                           JwtAuthenticationFilter jwtAuthenticationFilter,
                           CorsProperties corsProperties) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/ws/**",
                                "/oauth2/**", "/login/**",
                                "/api/auth/refresh",
                                "/actuator/health",
                                "/swagger-ui/**", "/v3/api-docs/**"
                        ).permitAll()
                        // 이벤트 채팅방은 로그인(+레벨) 사용자만 - 조회/전송 모두 인증 필요
                        .requestMatchers("/api/events/*/chat/**").authenticated()
                        // 지구본에 표시되는 이벤트 목록/상세는 비로그인 사용자도 볼 수 있어야 한다
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/events", "/api/events/*").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                // 인증이 없으면 Spring Security 기본값은 로그인 페이지로 302 리다이렉트하는 것이다.
                // 이 앱은 프론트엔드가 분리된 REST API 서버라 그러면 안 된다 — axios가 리다이렉트를
                // 따라가 구글 로그인 HTML을 받아버리고, 토큰 만료 시 동작해야 할 프론트엔드의
                // 401 인터셉터(리프레시 토큰 재발급)도 영영 트리거되지 않는다. 401로 명확히 응답한다.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(corsProperties.allowedOrigin()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
