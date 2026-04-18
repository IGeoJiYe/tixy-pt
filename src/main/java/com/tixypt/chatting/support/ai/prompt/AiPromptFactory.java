package com.tixypt.chatting.support.ai.prompt;

import com.tixypt.chatting.support.ai.model.AiPromptContext;
import com.tixypt.chatting.support.entity.SupportMessage;
import com.tixypt.chatting.support.enums.SupportMessageSenderType;
import com.tixypt.chatting.support.enums.SupportMessageType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// 최근 문의 흐름을 사람이 읽기 쉬운 프롬프트 본문으로 정리
// 모델에게 어떤 문맥을 어떤 순서로 보여 줄지를 담당한다
@Component
public class AiPromptFactory {

    private static final String EMPTY_VALUE = "없음";
    private static final String EMPTY_MESSAGE_CONTENT = "(빈 메시지)";
    private static final String RESPONSE_GUIDE = """
            아래 원칙만 지켜 답변하세요.
            - 답변 본문만 작성하고, 제목이나 대괄호 라벨은 출력하지 않습니다.
            - 고객 질문을 그대로 반복하지 않습니다.
            - 고객에게 바로 보여 줄 수 있는 자연스러운 한국어 2~4문장으로 답변합니다.
            - 아직 확정할 수 없는 내용은 단정하지 말고 상담원 확인이 필요하다고 안내합니다.
            - 주문번호나 예매 정보처럼 추가 확인이 필요한 경우 필요한 정보만 짧게 요청합니다.
            """;


    // roomId, 최신 고객 질문, 최근 대화 내역을 묶어서 provider 호출용 프롬프트를 만든다
    public AiPromptContext create(Long roomId, String latestCustomerMessage, List<SupportMessage> recentMessages) {
        return new AiPromptContext(
                latestCustomerMessage,
                """
                고객 지원 채팅에 들어갈 답변을 작성하세요.
                문의방 ID는 %s 입니다.
                
                고객의 마지막 질문:
                %s
                
                최근 대화 흐름(오래된 순):
                %s

                %s
                """.formatted(
                        roomId,
                        defaultIfBlank(latestCustomerMessage, EMPTY_VALUE),
                        formatRecentMessages(recentMessages),
                        RESPONSE_GUIDE.trim()
                )
        );
    }

    // 최근 메시지는 저장소에서 최신순으로 내려오니까 ai가 자연스럽게 읽을 수 있게 프롬프트에서는 다시 오래된 순으로 정렬해서 붙임
    private String formatRecentMessages(List<SupportMessage> recentMessages) {
        if (recentMessages.isEmpty()) {
            return EMPTY_VALUE;
        }

        List<SupportMessage> chronologicalMessages = new ArrayList<>(recentMessages);
        Collections.reverse(chronologicalMessages);

        return chronologicalMessages.stream()
                .map(message -> "[%s]{%s} %s".formatted(
                        senderLabel(message.getSenderType()),
                        messageTypeLabel(message.getMessageType()),
                        defaultIfBlank(message.getContent(), EMPTY_MESSAGE_CONTENT)
                ))
                .collect(Collectors.joining("\n"));
    }

    private String senderLabel(SupportMessageSenderType senderType) {
        return switch (senderType) {
            case USER -> "고객";
            case COUNSELOR -> "상담원";
            case AI -> "AI";
            case SYSTEM -> "시스템";
        };
    }

    private String messageTypeLabel(SupportMessageType messageType) {
        return switch (messageType) {
            case TEXT -> "일반";
            case SYSTEM -> "시스템";
        };
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
