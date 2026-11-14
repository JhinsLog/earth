package com.earth.config;

import com.earth.controller.ClientEventController;
import com.earth.logging.ApiActivityInterceptor;
import com.earth.logging.RequestBodyLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 브라우저 로그 전송은 쓰기 요청이지만 사용자의 행동이 아니다. 10초마다 오므로 남기면 활동 로그가 이것으로 덮인다.
        registry.addInterceptor(new ApiActivityInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns(ClientEventController.PATH);
    }

    /** 보안 필터보다 먼저 돌려, 인증·CORS 처리 전에 큰 본문을 끊는다. */
    @Bean
    public FilterRegistrationBean<RequestBodyLimitFilter> clientEventBodyLimitFilter(LogProperties logProperties) {
        var registration = new FilterRegistrationBean<>(new RequestBodyLimitFilter(logProperties.maxBodyBytes()));
        registration.addUrlPatterns(ClientEventController.PATH);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
