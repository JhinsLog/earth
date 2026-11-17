package com.earth.service;

import com.earth.domain.chat.ChatMessageRepository;
import com.earth.domain.flare.Flare;
import com.earth.domain.flare.FlareCategory;
import com.earth.domain.flare.FlareOrigin;
import com.earth.domain.flare.FlareRepository;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.FlareCreateRequest;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 축제·행사 플레어(스키장 개장 등)는 채팅을 닫는다. 화면이 채팅을 그리지 않는 것과 별개로 서버가 메시지를
 * 거절하는지 본다 — API로 직접 보내면 화면 숨김은 소용없다.
 */
class ChatClosedForExternalLeisureTest extends IntegrationTest {

    private static final String SOURCE = "TEST_LEISURE";
    private static final ExternalFlareSyncService.SystemAccount ACCOUNT =
            new ExternalFlareSyncService.SystemAccount("test-leisure", "테스트 레저 출처");

    @Autowired
    private ChatService chatService;

    @Autowired
    private FlareService flareService;

    @Autowired
    private ExternalFlareSyncService syncService;

    @Autowired
    private FlareRepository flareRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteAll();
        flareRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Flare external(String id, FlareOrigin origin, FlareCategory category) {
        syncService.sync(SOURCE, origin, ACCOUNT, List.of(new ExternalFlareSyncService.Draft(id, "제목", "내용", category,
                37.578, 128.316, Instant.now().plus(Duration.ofDays(3)))), false, Duration.ofHours(2), 0);
        return flareRepository.findBySourceAndExternalIdIn(SOURCE, List.of(id)).getFirst();
    }

    @Test
    @DisplayName("외부 레저/행사 플레어에는 채팅을 보낼 수 없고, 플레어도 닫힌 것으로 알린다")
    void externalLeisureIsClosed() {
        User sender = userRepository.save(new User(AuthProvider.GOOGLE, "sender", "보내는이"));
        Flare ski = external("ski", FlareOrigin.FESTIVAL, FlareCategory.LEISURE);

        assertThat(ski.isChatOpen()).isFalse();
        assertThatThrownBy(() -> chatService.postMessage(ski.getId(), sender, "안녕하세요"))
                .isInstanceOf(EarthApiException.class)
                .extracting(e -> ((EarthApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_CLOSED);
        assertThat(chatMessageRepository.count()).isZero();
    }

    @Test
    @DisplayName("외부 교통 돌발과 사용자가 남긴 레저/행사 플레어는 채팅이 열려 있다")
    void othersStayOpen() {
        User sender = userRepository.save(new User(AuthProvider.GOOGLE, "sender", "보내는이"));
        Flare accident = external("crash", FlareOrigin.PUBLIC, FlareCategory.ACCIDENT);
        // 채팅을 닫는 기준은 분류가 아니라 주체다(D-22). 공공 정보가 레저 분류로 와도 열려 있다.
        Flare publicLeisure = external("park", FlareOrigin.PUBLIC, FlareCategory.LEISURE);
        long userLeisure = flareService.create(sender,
                new FlareCreateRequest("동네 축제", "내용", FlareCategory.LEISURE, 37.5665, 126.9780)).id();

        assertThat(accident.isChatOpen()).isTrue();
        assertThat(publicLeisure.isChatOpen()).isTrue();
        chatService.postMessage(accident.getId(), sender, "여기 막혀요");
        chatService.postMessage(userLeisure, sender, "사람 많아요");
        assertThat(chatMessageRepository.count()).isEqualTo(2);
    }
}
