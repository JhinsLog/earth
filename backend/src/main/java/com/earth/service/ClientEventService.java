package com.earth.service;

import com.earth.config.LogProperties;
import com.earth.dto.ClientEventBatch;
import com.earth.dto.ClientEventBatch.ClientEvent;
import com.earth.dto.ClientEventBatch.ClientInfo;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 브라우저가 보낸 오류·행동 로그를 받아 {@code earth.client} 로거로 내보낸다(logback-spring.xml이 Loki로 보낸다).
 *
 * <p>DB에 넣지 않는다. 로그는 쓰기만 많고 고칠 일이 없으며 기간이 지나면 통째로 버리는 데이터다. 서비스 DB(512MB 제한)에
 * 쌓으면 플레어·채팅과 디스크·캐시를 다투고, 만료 삭제도 따로 짜야 한다. Loki는 이 용도의 저장소라 보관 기간(30일)이
 * 지나면 스스로 지운다.
 */
@Service
public class ClientEventService {

    private static final Logger log = LoggerFactory.getLogger("earth.client");
    private static final String RATE_KEY_PREFIX = "earth:client-events:";
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
    /** 줄바꿈·탭을 뺀 제어 문자. 로그를 사람이 읽는 화면에서 줄을 지우거나 색을 바꾸는 데 쓰일 수 있다. */
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");

    /** ChatService와 같은 이유로 INCR과 만료를 한 번에 건다(따로 보내면 만료 없는 키가 남을 수 있다). */
    private static final RedisScript<Long> INCREMENT_WITH_TTL = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final LogProperties properties;

    public ClientEventService(StringRedisTemplate redis, LogProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * @param clientIp 속도 제한 키로만 쓴다. 로그에는 남기지 않고, Redis에도 해시로만 1분간 둔다.
     * @param userId   로그인한 채로 보냈으면 그 사용자, 아니면 null
     * @param country  Cloudflare가 판정한 접속 국가(없으면 null)
     */
    public void record(ClientEventBatch batch, String clientIp, Long userId, String country) {
        checkRate(clientIp);
        ClientInfo client = batch.client();
        for (ClientEvent event : batch.events()) {
            LoggingEventBuilder line = (event.kind() == ClientEventBatch.Kind.ERROR ? log.atWarn() : log.atInfo())
                    .setMessage(event.kind() == ClientEventBatch.Kind.ERROR ? "client error" : "client action")
                    .addKeyValue("kind", event.kind().name().toLowerCase())
                    .addKeyValue("name", event.name())
                    .addKeyValue("sessionId", batch.sessionId())
                    .addKeyValue("clientAt", event.at());
            put(line, "userId", userId);
            put(line, "country", country);
            put(line, "path", clean(event.path()));
            put(line, "errorMessage", clean(event.message()));
            put(line, "errorStack", clean(event.stack()));
            if (event.data() != null) {
                // 대시보드에서 data_키 이름으로 바로 거른다. 키는 DTO에서 영숫자로만 받았다.
                event.data().forEach((key, value) -> put(line, "data_" + key, clean(value)));
            }
            if (client != null && event.kind() == ClientEventBatch.Kind.ERROR) {
                // 기기 정보는 오류에만 붙인다. 행동마다 붙이면 같은 문자열이 수백 번 반복돼 저장량만 늘어난다.
                put(line, "userAgent", clean(client.userAgent()));
                put(line, "gpu", clean(client.gpu()));
                put(line, "viewport", clean(client.viewport()));
                put(line, "pixelRatio", client.pixelRatio());
                put(line, "language", clean(client.language()));
            }
            line.log();
        }
    }

    /**
     * 한도를 넘으면 429로 거절한다. Redis가 응답하지 않으면 통과시킨다 — 로그 수집 때문에 사용자 화면에 오류가 날
     * 이유는 없고, 본문 크기·개수 상한은 그대로 걸려 있다.
     */
    private void checkRate(String clientIp) {
        String key = RATE_KEY_PREFIX + sha256(clientIp);
        Long count = redis.execute(INCREMENT_WITH_TTL, List.of(key), String.valueOf(RATE_WINDOW.toMillis()));
        if (count != null && count > properties.clientEventsPerMinute()) {
            throw new EarthApiException(ErrorCode.CLIENT_EVENT_RATE_LIMITED);
        }
    }

    private static void put(LoggingEventBuilder line, String key, Object value) {
        if (value != null) line.addKeyValue(key, value);
    }

    static String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return CONTROL.matcher(value).replaceAll("");
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
