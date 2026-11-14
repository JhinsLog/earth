package com.earth.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 본문을 읽기 전에 크기를 보고 거절한다. {@code POST /api/client-events}에만 건다.
 *
 * <p>컨트롤러의 {@code @Size} 검증은 Jackson이 본문 전체를 메모리에 올려 객체로 만든 <b>뒤에</b> 돈다. 인증 없이 열린
 * API라 수십 MB짜리 본문을 계속 보내면 검증에 닿기도 전에 힙이 찬다(운영 컨테이너는 1GB). 그래서 Content-Length만 보고
 * 먼저 끊는다. 길이를 밝히지 않는 청크 전송은 크기를 미리 알 수 없으므로 받지 않는다 — 브라우저의 fetch는 본문이
 * 문자열이면 항상 길이를 붙인다.
 */
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public RequestBodyLimitFilter(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod())) {
            long length = request.getContentLengthLong();
            if (length < 0) {
                response.sendError(HttpStatus.LENGTH_REQUIRED.value());
                return;
            }
            if (length > maxBytes) {
                response.sendError(HttpStatus.CONTENT_TOO_LARGE.value());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
