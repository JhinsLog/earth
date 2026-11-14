package com.earth.service;

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

/** 내 정보. 지금은 닉네임 변경만 있다. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final ContentModerator contentModerator;

    public UserService(UserRepository userRepository, ContentModerator contentModerator) {
        this.userRepository = userRepository;
        this.contentModerator = contentModerator;
    }

    /**
     * 닉네임을 바꾼다. 형식 → 욕설·개인정보 → 중복 순서로 검사한다.
     *
     * <p>중복 검사(exists)와 저장 사이에 다른 사람이 같은 닉네임을 먼저 저장할 수 있다. 그때는 DB의
     * lower(nickname) 유니크 인덱스(V11)가 막고, 그 위반을 같은 "이미 쓰이는 닉네임"으로 바꿔 돌려준다.
     * exists 검사는 대부분의 경우를 친절한 오류로 일찍 돌려주기 위한 것이고, 최종 보장은 인덱스다.
     */
    @Transactional
    public UserResponse changeNickname(Long userId, String requested) {
        String nickname = Nicknames.normalizeOrNull(requested);
        if (nickname == null) {
            throw new EarthApiException(ErrorCode.INVALID_NICKNAME);
        }
        contentModerator.check(nickname);

        User user = userRepository.findById(userId).orElseThrow(() -> new EarthApiException(ErrorCode.USER_NOT_FOUND));
        if (userRepository.existsByNicknameIgnoreCaseAndIdNot(nickname, userId)) {
            throw new EarthApiException(ErrorCode.NICKNAME_TAKEN);
        }
        user.changeNickname(nickname);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EarthApiException(ErrorCode.NICKNAME_TAKEN);
        }
        return UserResponse.from(user);
    }
}
