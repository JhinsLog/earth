package com.earth.service;

import com.earth.domain.event.EventCategory;
import com.earth.domain.event.EventRepository;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.EventCreateRequest;
import com.earth.dto.EventResponse;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 비로그인 사용자는 접속 국가의 별만 상세를 보고, 다른 나라 별은 위치와 존재만 받는다.
 */
class EventCountryVisibilityTest extends IntegrationTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    private User author;
    private long seoulId;
    private long dokdoId;
    private long tokyoId;

    @BeforeEach
    void setUp() {
        author = userRepository.save(new User(AuthProvider.GOOGLE, "author", "a@test.local", "작성자", null));
        seoulId = eventService.create(author, request("서울", 37.5665, 126.9780)).id();
        dokdoId = eventService.create(author, request("독도", 37.2414, 131.8697)).id();
        tokyoId = eventService.create(author, request("도쿄", 35.6762, 139.6503)).id();
    }

    @AfterEach
    void tearDown() {
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("등록할 때 국가가 판정돼 저장된다 — 독도는 대한민국")
    void countryIsAssignedOnCreate() {
        assertThat(eventRepository.findById(seoulId).orElseThrow().getCountryCode()).isEqualTo("KR");
        assertThat(eventRepository.findById(dokdoId).orElseThrow().getCountryCode()).isEqualTo("KR");
        assertThat(eventRepository.findById(tokyoId).orElseThrow().getCountryCode()).isEqualTo("JP");
    }

    @Test
    @DisplayName("한국에서 접속한 비로그인 사용자: 한국 별은 상세, 일본 별은 위치만")
    void anonymousSeesOwnCountryOnly() {
        Map<Long, EventResponse> byId = byId(eventService.findVisible(null, "KR", null, null, null, null));

        assertThat(byId.get(seoulId).restricted()).isFalse();
        assertThat(byId.get(seoulId).title()).isEqualTo("서울");
        assertThat(byId.get(dokdoId).restricted()).isFalse();

        EventResponse tokyo = byId.get(tokyoId);
        assertThat(tokyo.restricted()).isTrue();
        assertThat(tokyo.latitude()).isEqualTo(35.6762);
        assertThat(tokyo.title()).isNull();
        assertThat(tokyo.content()).isNull();
        assertThat(tokyo.category()).isNull();
        assertThat(tokyo.authorId()).isNull();
        assertThat(tokyo.authorNickname()).isNull();
    }

    @Test
    @DisplayName("접속 국가를 모르면 비로그인 사용자는 어느 별의 상세도 받지 못한다")
    void unknownCountrySeesNothingInDetail() {
        assertThat(eventService.findVisible(null, null, null, null, null, null))
                .allSatisfy(event -> assertThat(event.restricted()).isTrue());
    }

    @Test
    @DisplayName("로그인 사용자는 국가와 무관하게 전부 상세")
    void loggedInSeesEverything() {
        User viewer = userRepository.save(new User(AuthProvider.GOOGLE, "viewer", "v@test.local", "보는사람", null));

        assertThat(eventService.findVisible(viewer, "KR", null, null, null, null))
                .allSatisfy(event -> assertThat(event.restricted()).isFalse());
    }

    @Test
    @DisplayName("다른 나라 별의 단건 조회는 403")
    void anonymousCannotFetchOtherCountryDetail() {
        assertThat(eventService.findById(null, "KR", seoulId).title()).isEqualTo("서울");
        assertThatThrownBy(() -> eventService.findById(null, "KR", tokyoId))
                .isInstanceOfSatisfying(EarthApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COUNTRY_RESTRICTED));
    }

    private static Map<Long, EventResponse> byId(List<EventResponse> events) {
        return events.stream().collect(Collectors.toMap(EventResponse::id, Function.identity()));
    }

    private static EventCreateRequest request(String title, double lat, double lng) {
        return new EventCreateRequest(title, "내용", EventCategory.ETC, lat, lng);
    }
}
