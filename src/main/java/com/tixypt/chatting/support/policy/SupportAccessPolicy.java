package com.tixypt.chatting.support.policy;

import com.tixypt.api.member.entity.Member;
import com.tixypt.api.member.enums.MemberRole;
import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.enums.SupportRoomStatus;
import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;

import java.util.Objects;

// support 도메인에서 사용하는 역할 판정과 방 접근 규칙을 한곳에 모아둠
// 서비스마다 같은 권한 검사를 흩어 쓰지 않으려고 만듦
public final class SupportAccessPolicy {

    private SupportAccessPolicy() {
    }

    // 일반 상담원 여부 판정
    public static boolean isCounselor(Member member) {
        return member.getRole() == MemberRole.ADMIN;
    }

    // SUPER_ADMIN 여부 판정
    public static boolean isSuperAdmin(Member member) {
        return member.getRole() == MemberRole.SUPER_ADMIN;
    }

    // 운영 화면에 접근할 수 있는 사용자인지 확인할 때 사용
    public static boolean isOperator(Member member) {
        return isCounselor(member) || isSuperAdmin(member);
    }

    // 문의방 생성은 고객만 가능하니까 운영 계정 차단
    public static void validateCustomerOnly(Member member) {
        if (isOperator(member)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    // 운영 전용 API에서 공통으로 사용하는 권한 검증
    public static void validateOperator(Member member) {
        if (!isOperator(member)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    // 재배정, 강제 종료처럼 더 강한 운영 권한이 필요한 기능에 사용
    public static void validateSuperAdmin(Member member) {
        if (!isSuperAdmin(member)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    // 문의방 재배정 대상은 실제 상담 업무를 수행하는 ADMIN만 허용
    public static void validateAssignableCounselor(Member member) {
        if (!isCounselor(member)) {
            throw new SupportRoomException(SupportRoomErrorCode.INVALID_ROOM_ASSIGNMENT);
        }
    }

    // SUPER_ADMIN은 조회는 가능하지만 채팅 참여자처럼 메시지/읽음 처리를 하진 않음
    public static void validateParticipantWritable(Member member) {
        if (isSuperAdmin(member)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    // 방 상세/메시지/읽음 처리처럼 이 사용자가 이 방에 들어와도 되는가를 검증
    // SUPER_ADMIN은 전체 조회가 가능하고 상담원과 고객은 자신에게 연결된 방만 접근할 수 있음
    public static void validateRoomAccess(Member member, SupportRoom room) {
        if (isSuperAdmin(member)) {
            return;
        }

        if (isCounselor(member)) {
            if (!Objects.equals(room.getCounselorUserId(), member.getId())) {
                throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
            }
            return;
        }

        if (!Objects.equals(room.getCustomerUserId(), member.getId())) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    // 종료된 문의방은 이력 조회만 가능하고 새 메시지는 받지 않도록 막아서 닫힌 방 상태가 실시간 송신으로 다시 깨지지 않게 함
    public static void validateRoomWritable(SupportRoom room) {
        if (room.getStatus() == SupportRoomStatus.CLOSED) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ALREADY_CLOSED);
        }
    }


}
