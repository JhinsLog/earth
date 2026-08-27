package com.earth.security.jwt;

import com.earth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    /** HS256의 안전 요건. jjwt도 이보다 짧으면 거부하지만, 원인을 알려주는 메시지를 직접 낸다. */
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties jwtProperties;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.key = Keys.hmacShaKeyFor(validatedSecret(jwtProperties.secret()));
    }

    /**
     * 서명 키를 기동 시점에 검증한다.
     *
     * <p>예전에는 application.yml에 폴백 기본값이 있어서, 운영에서 JWT_SECRET을 빠뜨려도
     * 앱이 멀쩡히 떠서 <b>공개 저장소에 적힌 문자열로</b> 토큰에 서명했다. 그 줄을 읽은
     * 사람은 누구나 원하는 userId로 토큰을 위조할 수 있다. 게다가 아무 경고도 남지 않는다.
     * 잘못된 상태로 서비스가 뜨느니 아예 뜨지 않는 편이 안전하므로 여기서 실패시킨다.
     */
    private static byte[] validatedSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("""
                    JWT_SECRET이 설정되지 않아 기동할 수 없습니다.
                    backend/.env 에 아래처럼 서명 키를 넣어주세요 (최소 32바이트).
                      JWT_SECRET=$(openssl rand -base64 48)
                    운영 환경에서는 배포 시스템의 환경변수/시크릿으로 주입하세요.""");
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET이 너무 짧습니다 (%d바이트). HS256은 최소 %d바이트가 필요합니다. openssl rand -base64 48 로 생성하세요."
                            .formatted(keyBytes.length, MIN_SECRET_BYTES));
        }
        return keyBytes;
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, jwtProperties.accessTokenValidityMs());
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, jwtProperties.refreshTokenValidityMs());
    }

    private String createToken(Long userId, long validityMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validityMs))
                .signWith(key)
                .compact();
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
