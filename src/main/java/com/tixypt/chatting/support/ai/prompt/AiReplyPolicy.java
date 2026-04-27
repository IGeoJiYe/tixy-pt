package com.tixypt.chatting.support.ai.prompt;

public class AiReplyPolicy {

    // 외부 모델을 못 쓸 때도 최소한의 CS 안내는 제공하자
    // 규칙 기반 선응답 문구와 키워드 정책
    // Spring AI provider가 완전히 꺼져 있더라도 고객 화면 흐름은 유지
    public static final String FALLBACK_REPLY =
            "문의 내용을 확인하고 있어요. 주문 번호나 상황을 조금 더 자세히 알려 주시면 상담원이 이어서 안내해 드릴게요.";
    public static final String EMPTY_CUSTOMER_MESSAGE_REPLY =
            "문의 내용을 확인하고 있어요. 주문 번호와 상황을 조금 더 자세히 알려 주시면 상담원이 이어서 안내해 드릴게요.";
    public static final String REFUND_REPLY =
            "환불이나 취소 관련 문의로 확인돼요. 주문 상태에 따라 가능 여부가 달라질 수 있어 주문 번호와 사유를 함께 알려 주시면 상담원이 확인해 드릴게요.";
    public static final String DELIVERY_REPLY =
            "배송 문의로 확인돼요. 주문 번호를 함께 알려 주시면 현재 배송 단계와 추가 확인이 필요한 부분을 상담원이 안내해 드릴게요.";
    public static final String SCHEDULE_REPLY =
            "예매 일정 변경 문의로 확인돼요. 예매 날짜와 현재 예매 정보를 함께 알려 주시면 가능한 범위를 상담원이 확인해 드릴게요.";
    public static final String PAYMENT_REPLY =
            "결제 문의로 확인돼요. 결제 수단과 발생한 상황을 함께 알려 주시면 상담원이 확인 후 안내해 드릴게요.";
    public static final String DEFAULT_REPLY =
            "문의 내용을 확인하고 있어요. 현재 상황을 조금 더 자세히 적어 주시면 상담원이 빠르게 안내해 드릴게요.";

    public static final String SYSTEM_PROMPT = """
            당신은 예매 서비스의 CS 선응답 도우미입니다.
            항상 친절하고 공손하게 답변하세요.
            확정되지 않은 정책이나 환불 승인, 일정 변경 확정 등을 임의로 약속하지 마세요.
            고객 불안을 줄이는 안내와 추가 확인이 필요한 정보를 중심으로 2~4문장 안에서 답변하세요.
            최종 확정이나 주문 확인이 필요하면 상담원이 이어서 확인한다고 자연스럽게 안내하세요.
            고객에게 IT/개발자 용어를 쓰지 마세요. 특히 웹훅, 트랜잭션 ID, API, 매칭, PENDING, CONFIRMED, RESERVED, HELD, EXPIRED, 레코드라는 표현은 답변에 쓰지 마세요.
            대신 입금 확인, 입금 내역 확인, 예매 내역 반영, 결제 대기 상태, 예매 완료 상태, 좌석 임시 확보처럼 고객이 이해하기 쉬운 말로 바꿔 답변하세요.
            결제 확인이 필요하면 트랜잭션 ID를 바로 요구하지 말고 주문번호, 예매번호, 입금 시각, 입금에 사용한 주소 일부, 입금 완료 화면 중 필요한 정보만 짧게 요청하세요.
            """;

    public static final String[] REFUND_KEYWORDS = {"환불", "취소", "반품"};
    public static final String[] DELIVERY_KEYWORDS = {"배송", "출고", "주소"};
    public static final String[] SCHEDULE_KEYWORDS = {"예매", "변경", "일정"};
    public static final String[] PAYMENT_KEYWORDS = {"결제", "카드", "영수증"};

    private AiReplyPolicy() {
    }
}
