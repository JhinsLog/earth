package com.earth.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    public static final int NICKNAME_MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    @Column(nullable = false, length = NICKNAME_MAX_LENGTH)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(nullable = false)
    private int level = 1;

    @Column(nullable = false)
    private int exp = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 구글에서는 계정 ID(sub)만 받는다. 이메일·이름·사진은 서비스에 필요하지 않아 받지도 저장하지도 않는다
     * (docs/decisions.md D-16). 닉네임은 가입 때 자동으로 정하고 사용자가 바꿀 수 있다.
     */
    public User(AuthProvider provider, String providerId, String nickname) {
        this.provider = provider;
        this.providerId = providerId;
        this.nickname = nickname;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public boolean hasAtLeastLevel(int requiredLevel) {
        return this.level >= requiredLevel;
    }
}
