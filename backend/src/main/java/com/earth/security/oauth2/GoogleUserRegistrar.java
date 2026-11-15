package com.earth.security.oauth2;

import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.Nicknames;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구글 계정 ID(sub)로 Earth 사용자를 찾고, 처음이면 자동 닉네임으로 만든다. 트랜잭션은 여기서만 연다.
 *
 * <p>{@link EarthOidcUserService}와 나눈 이유: 같은 클래스 안의 메서드에 트랜잭션을 걸어도 자기 호출은 프록시를
 * 거치지 않아 효과가 없다. 별도 빈으로 둬야 저장 구간에만 트랜잭션이 걸린다(2026-11-14).
 */
@Component
public class GoogleUserRegistrar {

    // 자동 닉네임이 이미 있는 것과 겹치면 다시 뽑는다. 약 10억 가지라 몇 번 안에 끝난다.
    private static final int MAX_NICKNAME_ATTEMPTS = 5;

    private final UserRepository userRepository;

    public GoogleUserRegistrar(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User findOrCreate(String googleId) {
        return userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, googleId)
                .orElseGet(() -> userRepository.save(new User(AuthProvider.GOOGLE, googleId, uniqueRandomNickname())));
    }

    private String uniqueRandomNickname() {
        for (int i = 0; i < MAX_NICKNAME_ATTEMPTS; i++) {
            String candidate = Nicknames.random();
            if (!userRepository.existsByNicknameKey(Nicknames.key(candidate))) return candidate;
        }
        throw new IllegalStateException("자동 닉네임을 " + MAX_NICKNAME_ATTEMPTS + "번 뽑았지만 모두 이미 쓰이고 있습니다.");
    }
}
