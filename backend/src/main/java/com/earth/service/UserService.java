package com.earth.service;

import com.earth.domain.user.NicknameHoldRepository;
import com.earth.domain.user.Nicknames;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.UserResponse;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import com.earth.moderation.ContentModerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** 내 정보. 지금은 닉네임 변경만 있다. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final NicknameHoldRepository nicknameHoldRepository;
    private final ContentModerator contentModerator;

    public UserService(UserRepository userRepository, NicknameHoldRepository nicknameHoldRepository,
                       ContentModerator contentModerator) {
        this.userRepository = userRepository;
        this.nicknameHoldRepository = nicknameHoldRepository;
        this.contentModerator = contentModerator;
    }

    /**
     * 닉네임을 바꾼다. 형식 → 욕설·개인정보 → 변경 주기 → 중복 순서로 검사한다.
     *
     * <p>중복은 눈으로 구별되지 않는 이름까지 같은 이름으로 본다(비교용 키, D-17). 다른 사람이 30일 안에
     * 버린 이름도 쓰인 이름으로 본다. 버리자마자 남이 가져가면 원래 주인으로 착각하게 되기 때문이다.
     *
     * <p>중복 검사(exists)와 저장 사이에 다른 사람이 같은 이름을 먼저 저장할 수 있다. 그때는 DB의
     * nickname_key 유니크 제약(V12)이 막고, 그 위반을 같은 "이미 쓰이는 닉네임"으로 바꿔 돌려준다.
     * exists 검사는 대부분의 경우를 친절한 오류로 일찍 돌려주기 위한 것이고, 최종 보장은 제약이다.
     */
    @Transactional
    public UserResponse changeNickname(Long userId, String requested) {
        String nickname = Nicknames.normalizeOrNull(requested);
        if (nickname == null) {
            throw new EarthApiException(ErrorCode.INVALID_NICKNAME);
        }
        contentModerator.check(nickname);

        User user = userRepository.findById(userId).orElseThrow(() -> new EarthApiException(ErrorCode.USER_NOT_FOUND));
        String key = Nicknames.key(nickname);
        String oldNickname = user.getNickname();
        String oldKey = user.getNicknameKey();
        Instant now = Instant.now();

        // 대소문자·띄어쓰기만 다듬는 것은 같은 이름이라 주기·중복 검사 없이 바꾼다.
        if (!key.equals(oldKey)) {
            Instant changeableAt = user.nicknameChangeableAt(now);
            if (changeableAt != null) {
                long days = Math.max(1, (Duration.between(now, changeableAt).toSeconds() + 86_399) / 86_400); // 남은 날 올림
                throw new EarthApiException(ErrorCode.NICKNAME_CHANGE_TOO_SOON,
                        "닉네임은 30일에 한 번 바꿀 수 있습니다. " + days + "일 뒤에 다시 바꿔 주세요.");
            }
            if (userRepository.existsByNicknameKeyAndIdNot(key, userId)
                    || nicknameHoldRepository.existsByNicknameKeyAndUserIdNotAndReleasedAtAfter(
                            key, userId, now.minus(Nicknames.CHANGE_COOLDOWN))) {
                throw new EarthApiException(ErrorCode.NICKNAME_TAKEN);
            }
            // 자동 닉네임은 무작위라 남이 흉내 낼 가치가 없고, 사용자가 이 형식으로 정할 수도 없어 잡아 두지 않는다.
            if (!Nicknames.isAuto(oldNickname)) {
                nicknameHoldRepository.hold(oldKey, userId, now);
            }
        }
        user.changeNickname(nickname, now);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EarthApiException(ErrorCode.NICKNAME_TAKEN);
        }
        return UserResponse.from(user);
    }
}
