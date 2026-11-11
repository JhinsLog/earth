package com.earth.service;

import com.earth.config.ChatProperties;
import com.earth.domain.chat.ChatMessage;
import com.earth.domain.chat.ChatMessagePosted;
import com.earth.domain.chat.ChatMessageRepository;
import com.earth.domain.flare.Flare;
import com.earth.domain.user.User;
import com.earth.dto.ChatMessageResponse;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class ChatService {

    private static final String FLOOD_KEY_PREFIX = "earth:chat-rate:";
    private static final String SAME_CONTENT_KEY_PREFIX = "earth:chat-same:";
    private static final Duration FLOOD_WINDOW = Duration.ofMinutes(1);

    /**
     * 카운터를 1 올리고, 처음 만들어진 키에만 만료 시간을 건다. 한 번에 실행된다.
     *
     * <p>예전에는 INCR과 EXPIRE를 따로 보냈다. 그 사이에 연결이 끊기거나 서버가 죽으면 만료 없는 키가
     * 남아, 그 사용자는 영영 제한에 걸린다. Redis는 스크립트를 다른 명령과 섞지 않고 통째로 실행하므로
     * 두 동작 사이에 틈이 없다.
     */
    private static final RedisScript<Long> INCREMENT_WITH_TTL = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final ChatMessageRepository chatMessageRepository;
    private final ApplicationEventPublisher events;
    private final FlareService flareService;
    private final ChatProperties chatProperties;
    private final StringRedisTemplate stringRedisTemplate;

    public ChatService(ChatMessageRepository chatMessageRepository,
                        ApplicationEventPublisher events,
                        FlareService flareService,
                        ChatProperties chatProperties,
                        StringRedisTemplate stringRedisTemplate) {
        this.chatMessageRepository = chatMessageRepository;
        this.events = events;
        this.flareService = flareService;
        this.chatProperties = chatProperties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public List<ChatMessageResponse> findRecent(Long flareId) {
        // 채팅방은 플레어와 수명을 같이한다. 만료·삭제된 플레어의 대화는 열지 않는다.
        Flare flare = flareService.getVisibleOrThrow(flareId);
        List<ChatMessageResponse> recentDesc = chatMessageRepository.findTop100ByFlareOrderByCreatedAtDesc(flare)
                .stream().map(ChatMessageResponse::from).toList();
        List<ChatMessageResponse> chronological = new java.util.ArrayList<>(recentDesc);
        Collections.reverse(chronological);
        return chronological;
    }

    @Transactional
    public ChatMessageResponse postMessage(Long flareId, User sender, String content) {
        if (!sender.hasAtLeastLevel(chatProperties.minLevelToJoin())) {
            throw new EarthApiException(ErrorCode.LEVEL_NOT_ENOUGH);
        }
        checkFloodLimit(sender);
        checkSameContentLimit(sender, content);
        Flare flare = flareService.getVisibleOrThrow(flareId);
        ChatMessage message = new ChatMessage(flare, sender, content);
        chatMessageRepository.save(message);

        // 전파는 커밋된 뒤에 일어난다. 이유는 ChatMessagePosted 주석 참고.
        ChatMessageResponse response = ChatMessageResponse.from(message);
        events.publishEvent(new ChatMessagePosted(flareId, response));
        return response;
    }

    /**
     * 1분 단위 고정 윈도우로 도배를 막는다.
     *
     * <p>Redis 카운터를 쓰는 이유는 두 가지다. 인스턴스를 여러 대로 늘려도 제한이 그대로
     * 유지되고, 매 메시지마다 DB를 조회하지 않아도 된다. 첫 증가 때만 TTL을 걸어
     * 키가 알아서 사라지게 한다.
     */
    private void checkFloodLimit(User sender) {
        Long count = incrementWithTtl(FLOOD_KEY_PREFIX + sender.getId(), FLOOD_WINDOW);
        if (count == null) return; // Redis가 응답하지 않는 상황에서 채팅까지 막지는 않는다

        if (count > chatProperties.maxPerMinute()) {
            throw new EarthApiException(ErrorCode.CHAT_RATE_LIMIT_EXCEEDED,
                    "1분에 최대 %d개까지 보낼 수 있습니다.".formatted(chatProperties.maxPerMinute()));
        }
    }

    /**
     * 같은 내용을 일정 기간 안에 정해진 횟수까지만 받는다. 채팅방을 가리지 않는다. 같은 문구를
     * 여러 방에 돌리는 것도 도배다.
     *
     * <p>공백·대소문자만 바꿔 우회하지 못하도록 정규화한 뒤 해시를 키로 쓴다. 해시를 쓰는 이유는
     * 본문(최대 500자)을 키에 그대로 넣지 않기 위해서다. 키 길이가 일정해지고 Redis에 대화 원문이 남지 않는다.
     */
    private void checkSameContentLimit(User sender, String content) {
        String key = SAME_CONTENT_KEY_PREFIX + sender.getId() + ":" + sha256(normalize(content));
        Long count = incrementWithTtl(key, Duration.ofMinutes(chatProperties.sameContentWindowMinutes()));
        if (count == null) return; // 도배 제한과 같은 이유로 Redis가 응답하지 않으면 통과시킨다

        if (count > chatProperties.maxSameContent()) {
            throw new EarthApiException(ErrorCode.CHAT_DUPLICATE_CONTENT,
                    "같은 내용은 %d분 안에 %d번까지만 보낼 수 있습니다."
                            .formatted(chatProperties.sameContentWindowMinutes(), chatProperties.maxSameContent()));
        }
    }

    private Long incrementWithTtl(String key, Duration ttl) {
        return stringRedisTemplate.execute(INCREMENT_WITH_TTL, List.of(key), String.valueOf(ttl.toMillis()));
    }

    /** 공백 종류·개수와 대소문자만 다른 내용을 같은 것으로 본다. */
    static String normalize(String content) {
        return content.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256은 모든 JVM이 제공해야 하는 알고리즘이다", e);
        }
    }
}
