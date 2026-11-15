package com.earth.security.oauth2;

import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 구글 로그인으로 가입할 때 계정 ID 말고는 아무것도 받지 않고, 실명 대신 자동 닉네임이 붙는지 검증한다(D-16).
 * 닉네임 유니크 인덱스(V11)는 실제 PostgreSQL에서만 확인되므로 통합 테스트로 둔다.
 */
class GoogleUserRegistrarTest extends IntegrationTest {

    @Autowired
    private GoogleUserRegistrar registrar;

    @Autowired
    private EarthOidcUserService oidcUserService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("처음 로그인하면 자동 닉네임(지구인-XXXXXX)으로 가입된다")
    void signUpWithRandomNickname() {
        User user = registrar.findOrCreate("google-sub-1");

        assertThat(user.getNickname()).matches("지구인-[A-HJ-KM-NP-Z2-9]{6}");
    }

    @Test
    @DisplayName("같은 구글 계정으로 다시 로그인하면 같은 사용자이고, 바꾼 닉네임이 유지된다")
    void loginAgainKeepsUserAndNickname() {
        User first = registrar.findOrCreate("google-sub-2");
        first.changeNickname("별헤는밤", Instant.now());
        userRepository.save(first);

        User again = registrar.findOrCreate("google-sub-2");

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(again.getNickname()).isEqualTo("별헤는밤");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("자동 닉네임은 사용자마다 다르다")
    void randomNicknamesDiffer() {
        User a = registrar.findOrCreate("google-sub-a");
        User b = registrar.findOrCreate("google-sub-b");

        assertThat(a.getNickname()).isNotEqualTo(b.getNickname());
    }

    @Test
    @DisplayName("DB가 대소문자·구분 기호만 다른 닉네임도 막는다 — 서비스 검사를 건너뛴 저장도 V12 제약이 거절한다")
    void databaseRejectsCaseInsensitiveDuplicate() {
        User a = registrar.findOrCreate("google-sub-c");
        a.changeNickname("Earth", Instant.now());
        userRepository.saveAndFlush(a);
        User b = registrar.findOrCreate("google-sub-d");
        b.changeNickname("ear_th", Instant.now());

        assertThatThrownBy(() -> userRepository.saveAndFlush(b)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("로그인 처리 서비스는 프록시가 아니다 — 트랜잭션은 저장 빈에만 걸린다")
    void oidcUserServiceIsNotTransactionalProxy() {
        assertThat(AopUtils.isAopProxy(oidcUserService)).isFalse();
        assertThat(AopUtils.isAopProxy(registrar)).isTrue();
    }
}
