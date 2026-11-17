package com.earth.domain.flare;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {

    List<ModerationAction> findByFlareIdOrderByCreatedAt(Long flareId);
}
