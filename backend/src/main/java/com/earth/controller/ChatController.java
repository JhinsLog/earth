package com.earth.controller;

import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.ChatMessageRequest;
import com.earth.dto.ChatMessageResponse;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import com.earth.exception.ErrorResponse;
import com.earth.service.ChatService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/events/{eventId}/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final UserRepository userRepository;

    public ChatController(ChatService chatService, UserRepository userRepository) {
        this.chatService = chatService;
        this.userRepository = userRepository;
    }

    /** 채팅방 입장 시 최근 대화 내역(최대 100건, 오래된 순)을 REST로 먼저 불러오고, 이후는 STOMP로 실시간 수신한다. */
    @GetMapping
    public List<ChatMessageResponse> history(@PathVariable Long eventId) {
        return chatService.findRecent(eventId);
    }

    /**
     * 클라이언트는 /app/chat.{eventId}.send 로 발행하고, 결과는 Redis를 거쳐
     * /topic/chat.{eventId} 를 구독 중인 모든 인스턴스의 클라이언트에게 전달된다.
     */
    // @Valid가 없으면 DTO의 @NotBlank/@Size가 전혀 적용되지 않는다. 공백만 있는 메시지가
    // 그대로 저장·전파되고, 상한을 넘긴 본문은 DB 컬럼 제약에 걸려 500 오류로 나간다.
    @MessageMapping("/chat.{eventId}.send")
    public void send(@DestinationVariable Long eventId, @Valid @Payload ChatMessageRequest request, Principal principal) {
        if (principal == null) {
            throw new EarthApiException(ErrorCode.LOGIN_REQUIRED);
        }
        User sender = userRepository.findById(Long.valueOf(principal.getName()))
                .orElseThrow(() -> new EarthApiException(ErrorCode.USER_NOT_FOUND));
        chatService.postMessage(eventId, sender, request.content());
    }

    /*
     * @MessageMapping에서 던진 예외는 기본적으로 서버 로그에만 남고 클라이언트에는 아무것도
     * 가지 않는다. 전송 버튼을 눌러도 정말 아무 반응이 없어 원인 파악이 불가능했던 이유다.
     * 보낸 사람에게만(broadcast = false) 실패 사유를 되돌려준다.
     */
    @MessageExceptionHandler(EarthApiException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ErrorResponse handleEarthApiException(EarthApiException e) {
        return ErrorResponse.of(e.getErrorCode(), e.getMessage());
    }

    /** 입력 검증 실패는 사용자가 고칠 수 있는 문제이므로 어떤 항목이 왜 틀렸는지 알려준다. */
    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException e) {
        String message = Optional.ofNullable(e.getBindingResult())
                .map(BindingResult::getFieldErrors)
                .flatMap(errors -> errors.stream().findFirst())
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse("잘못된 요청입니다.");
        return new ErrorResponse("INVALID_REQUEST", message);
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ErrorResponse handleUnexpectedException(Exception e) {
        log.error("채팅 메시지 처리 중 예기치 못한 오류", e);
        return new ErrorResponse("INTERNAL_ERROR", "메시지 전송에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }
}
