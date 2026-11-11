package com.earth.service;

import com.earth.domain.user.User;
import com.earth.domain.user.UserBlockRepository;
import com.earth.domain.user.UserRepository;
import com.earth.dto.BlockedUserResponse;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사용자 차단. 목록만 관리하고, 차단된 사람의 글을 거르는 일은 화면이 한다(docs/decisions.md D-13).
 * 차단과 해제는 몇 번을 불러도 결과가 같다(이미 차단했으면 그대로, 차단하지 않았으면 그대로).
 */
@Service
@Transactional(readOnly = true)
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;

    public UserBlockService(UserBlockRepository userBlockRepository, UserRepository userRepository) {
        this.userBlockRepository = userBlockRepository;
        this.userRepository = userRepository;
    }

    public List<BlockedUserResponse> findMine(User blocker) {
        return userBlockRepository.findAllByBlocker(blocker.getId()).stream().map(BlockedUserResponse::from).toList();
    }

    @Transactional
    public void block(User blocker, Long targetId) {
        if (blocker.getId().equals(targetId)) {
            throw new EarthApiException(ErrorCode.CANNOT_BLOCK_SELF);
        }
        if (!userRepository.existsById(targetId)) {
            throw new EarthApiException(ErrorCode.USER_NOT_FOUND);
        }
        userBlockRepository.insertIgnoringDuplicate(blocker.getId(), targetId);
    }

    @Transactional
    public void unblock(User blocker, Long targetId) {
        userBlockRepository.deleteBlock(blocker.getId(), targetId);
    }
}
