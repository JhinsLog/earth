package com.earth.domain.chat;

import com.earth.domain.event.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop100ByEventOrderByCreatedAtDesc(Event event);
}
