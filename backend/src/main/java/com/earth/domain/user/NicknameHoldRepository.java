package com.earth.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface NicknameHoldRepository extends JpaRepository<NicknameHold, String> {

    /** 버린 닉네임을 잡아 둔다. 이미 잡혀 있으면(예전에 누가 버렸던 이름) 지금 버린 사람과 시각으로 덮어쓴다. */
    @Modifying
    @Query(value = """
            INSERT INTO nickname_holds (nickname_key, user_id, released_at) VALUES (:key, :userId, :releasedAt)
            ON CONFLICT (nickname_key) DO UPDATE SET user_id = EXCLUDED.user_id, released_at = EXCLUDED.released_at
            """, nativeQuery = true)
    void hold(@Param("key") String nicknameKey, @Param("userId") Long userId, @Param("releasedAt") Instant releasedAt);

    /** 다른 사람이 cutoff 이후에 버려서 아직 잡혀 있는 이름인가. 기간이 지난 행은 남아 있어도 무시한다. */
    boolean existsByNicknameKeyAndUserIdNotAndReleasedAtAfter(String nicknameKey, Long userId, Instant cutoff);
}
