package com.earth.domain.event;

import com.earth.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventConfirmationRepository extends JpaRepository<EventConfirmation, Long> {

    Optional<EventConfirmation> findByEventAndUser(Event event, User user);

    boolean existsByEventAndUser(Event event, User user);

    /** 목록 조회에서 "내가 공감한 별"을 한 번의 쿼리로 가려내기 위한 것. */
    List<EventConfirmation> findByUserAndEventIn(User user, List<Event> events);
}
