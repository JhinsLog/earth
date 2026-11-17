package com.earth.domain.flare;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlareConfirmationEventRepository extends JpaRepository<FlareConfirmationEvent, Long> {

    List<FlareConfirmationEvent> findByFlareIdOrderByIdAsc(Long flareId);
}
