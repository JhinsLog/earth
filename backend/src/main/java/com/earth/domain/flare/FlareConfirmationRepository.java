package com.earth.domain.flare;

import com.earth.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlareConfirmationRepository extends JpaRepository<FlareConfirmation, Long> {

    Optional<FlareConfirmation> findByFlareAndUser(Flare flare, User user);

    boolean existsByFlareAndUser(Flare flare, User user);

    /** 목록 조회에서 "내가 공감한 플레어"을 한 번의 쿼리로 가려내기 위한 것. */
    List<FlareConfirmation> findByUserAndFlareIn(User user, List<Flare> flares);
}
