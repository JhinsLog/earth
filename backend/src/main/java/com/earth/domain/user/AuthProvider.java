package com.earth.domain.user;

public enum AuthProvider {
    GOOGLE,
    /**
     * 외부 데이터로 만든 별의 작성자. 로그인 경로가 없는 계정이다 — OAuth는 GOOGLE로만 사용자를 찾으므로
     * 이 제공자의 계정으로는 토큰이 발급되지 않는다.
     */
    SYSTEM
}
