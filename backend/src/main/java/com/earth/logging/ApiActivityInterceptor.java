package com.earth.logging;

import com.earth.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 로그인한 사용자의 쓰기 요청(POST·PUT·PATCH·DELETE)을 하나도 빠짐없이 활동 로그로 남긴다.
 *
 * <p>서비스 메서드마다 로그 줄을 넣지 않고 여기 한 곳에서 처리한다. 새 API가 생겨도 기록이 저절로 따라오고,
 * 실패한 시도(403·429 등)도 상태 코드와 함께 남는다 — 도배·권한 우회 시도를 보려면 실패 쪽이 더 중요하다.
 *
 * <p>행동 이름은 실제 경로가 아니라 경로 패턴({@code POST /api/flares/{flareId}/confirm})으로 쓴다. 실제 경로를 쓰면
 * 플레어마다 이름이 달라져 "공감이 몇 번 눌렸나"를 셀 수 없다. 경로 변수(flareId 등)는 따로 필드로 붙인다.
 *
 * <p>조회(GET)는 남기지 않는다. 지도를 움직일 때마다 플레어 목록을 다시 불러 양이 쓰기의 수백 배이고, 화면을 본 기록은
 * 브라우저 행동 로그가 맡는다.
 */
public class ApiActivityInterceptor implements HandlerInterceptor {

    private static final String STARTED_AT = ApiActivityInterceptor.class.getName() + ".startedAt";
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        request.setAttribute(STARTED_AT, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        if (READ_METHODS.contains(request.getMethod())) return;
        Long userId = currentUserId();
        if (userId == null) return;

        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern == null) return;

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("source", "api");
        fields.put("status", response.getStatus());
        Object startedAt = request.getAttribute(STARTED_AT);
        if (startedAt instanceof Long start) {
            fields.put("durationMs", (System.nanoTime() - start) / 1_000_000);
        }
        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables) {
            variables.forEach((name, value) -> fields.put(String.valueOf(name), String.valueOf(value)));
        }
        ActivityLog.record(userId, request.getMethod() + " " + pattern, fields);
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }
}
