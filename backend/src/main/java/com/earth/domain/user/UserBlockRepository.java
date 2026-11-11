package com.earth.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlock.Key> {

    /** 이미 차단했으면 아무것도 하지 않는다. 동시 요청도 기본 키 제약으로 한 행만 남는다. */
    @Modifying
    @Query(value = """
            INSERT INTO user_blocks (blocker_id, blocked_id) VALUES (:blockerId, :blockedId)
            ON CONFLICT (blocker_id, blocked_id) DO NOTHING
            """, nativeQuery = true)
    int insertIgnoringDuplicate(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    @Modifying
    @Query("DELETE FROM UserBlock b WHERE b.blockerId = :blockerId AND b.blocked.id = :blockedId")
    int deleteBlock(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    @Query("SELECT b FROM UserBlock b JOIN FETCH b.blocked WHERE b.blockerId = :blockerId ORDER BY b.createdAt DESC")
    List<UserBlock> findAllByBlocker(@Param("blockerId") Long blockerId);
}
