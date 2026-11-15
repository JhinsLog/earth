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

    /** 같은 이름인지 판정하는 키({@link Nicknames#key}). 유니크 제약이 이 칸에 걸려 있다(V12). */
    @Column(name = "nickname_key", nullable = false, length = NICKNAME_MAX_LENGTH)
    private String nicknameKey;

    /** 사용자가 마지막으로 닉네임을 바꾼 시각. 자동 닉네임만 쓰는 동안은 null. */
    @Column(name = "nickname_changed_at")
    private Instant nicknameChangedAt;

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
        this.nicknameKey = Nicknames.key(nickname);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /**
     * 닉네임을 바꾼다. 키가 같은 변경(대소문자·띄어쓰기만 다름)은 같은 이름을 다듬는 것이라
     * 변경 주기에 넣지 않는다.
     */
    public void changeNickname(String nickname, Instant now) {
        String key = Nicknames.key(nickname);
        if (!key.equals(this.nicknameKey)) {
            this.nicknameChangedAt = now;
        }
        this.nickname = nickname;
        this.nicknameKey = key;
    }

    /** 다음에 다른 이름으로 바꿀 수 있는 시각. 지금 바로 바꿀 수 있으면 null. */
    public Instant nicknameChangeableAt(Instant now) {
        if (nicknameChangedAt == null) return null;
        Instant next = nicknameChangedAt.plus(Nicknames.CHANGE_COOLDOWN);
        return next.isAfter(now) ? next : null;
    }

    public boolean hasAtLeastLevel(int requiredLevel) {
        return this.level >= requiredLevel;
    }
}
