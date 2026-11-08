package com.earth.service;

import com.earth.domain.flare.FlareCategory;
import com.earth.domain.flare.FlareRepository;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.FlareCreateRequest;
import com.earth.dto.FlareResponse;
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
 * 비로그인 사용자는 접속 국가의 플레어만 상세를 보고, 다른 나라 플레어는 위치와 존재만 받는다.
 */
class FlareCountryVisibilityTest extends IntegrationTest {

    @Autowired
    private FlareService flareService;

    @Autowired
    private FlareRepository flareRepository;

    @Autowired
    private UserRepository userRepository;

    private User author;
    private long seoulId;
    private long dokdoId;
    private long tokyoId;

    @BeforeEach
    void setUp() {
        author = userRepository.save(new User(AuthProvider.GOOGLE, "author", "a@test.local", "작성자", null));
        seoulId = flareService.create(author, request("서울", 37.5665, 126.9780)).id();
        dokdoId = flareService.create(author, request("독도", 37.2414, 131.8697)).id();
        tokyoId = flareService.create(author, request("도쿄", 35.6762, 139.6503)).id();
    }

    @AfterEach
    void tearDown() {
        flareRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("등록할 때 국가가 판정돼 저장된다 — 독도는 대한민국")
    void countryIsAssignedOnCreate() {
        assertThat(flareRepository.findById(seoulId).orElseThrow().getCountryCode()).isEqualTo("KR");
        assertThat(flareRepository.findById(dokdoId).orElseThrow().getCountryCode()).isEqualTo("KR");
        assertThat(flareRepository.findById(tokyoId).orElseThrow().getCountryCode()).isEqualTo("JP");
    }

    @Test
    @DisplayName("한국에서 접속한 비로그인 사용자: 한국 플레어는 상세, 일본 플레어는 위치만")
    void anonymousSeesOwnCountryOnly() {
        Map<Long, FlareResponse> byId = byId(flareService.findVisible(null, "KR", null, null, null, null));

        assertThat(byId.get(seoulId).restricted()).isFalse();
        assertThat(byId.get(seoulId).title()).isEqualTo("서울");
        assertThat(byId.get(dokdoId).restricted()).isFalse();

        FlareResponse tokyo = byId.get(tokyoId);
        assertThat(tokyo.restricted()).isTrue();
        assertThat(tokyo.latitude()).isEqualTo(35.6762);
        assertThat(tokyo.title()).isNull();
        assertThat(tokyo.content()).isNull();
        assertThat(tokyo.category()).isNull();
        assertThat(tokyo.authorId()).isNull();
        assertThat(tokyo.authorNickname()).isNull();
    }

    @Test
    @DisplayName("접속 국가를 모르면 비로그인 사용자는 어느 플레어의 상세도 받지 못한다")
    void unknownCountrySeesNothingInDetail() {
        assertThat(flareService.findVisible(null, null, null, null, null, null))
                .allSatisfy(flare -> assertThat(flare.restricted()).isTrue());
    }

    @Test
    @DisplayName("로그인 사용자는 국가와 무관하게 전부 상세")
    void loggedInSeesEverything() {
        User viewer = userRepository.save(new User(AuthProvider.GOOGLE, "viewer", "v@test.local", "보는사람", null));

        assertThat(flareService.findVisible(viewer, "KR", null, null, null, null))
                .allSatisfy(flare -> assertThat(flare.restricted()).isFalse());
    }

    @Test
    @DisplayName("다른 나라 플레어의 단건 조회는 403")
    void anonymousCannotFetchOtherCountryDetail() {
        assertThat(flareService.findById(null, "KR", seoulId).title()).isEqualTo("서울");
        assertThatThrownBy(() -> flareService.findById(null, "KR", tokyoId))
                .isInstanceOfSatisfying(EarthApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COUNTRY_RESTRICTED));
    }

    private static Map<Long, FlareResponse> byId(List<FlareResponse> flares) {
        return flares.stream().collect(Collectors.toMap(FlareResponse::id, Function.identity()));
    }

    private static FlareCreateRequest request(String title, double lat, double lng) {
        return new FlareCreateRequest(title, "내용", FlareCategory.ETC, lat, lng);
    }
}
