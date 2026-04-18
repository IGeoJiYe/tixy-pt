package com.tixypt.chatting.support.policy;

import com.tixypt.chatting.support.entity.SupportRoom;
import com.tixypt.chatting.support.enums.SupportRoomStatus;
import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import com.tixypt.core.security.dto.LoginUserInfoDto;

import java.util.Objects;

// support 도메인에서 사용하는 역할 판정과 방 접근 규칙을 한곳에 모아둠
// 서비스마다 같은 권한 검사를 흩어 쓰지 않으려고 만듦
public final class SupportAccessPolicy {

    private static final String ROLE_USER = "ROLE_USER";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    private SupportAccessPolicy() {
    }

    // 일반 고객인지 확인
    public static boolean isCustomer(LoginUserInfoDto loginUser) {
        return hasRole(loginUser, ROLE_USER);
    }

    // 실제 상담 업무 수행하는 ADMIN인지 확인
    public static boolean isCounselor(LoginUserInfoDto loginUser) {
        return hasRole(loginUser, ROLE_ADMIN);
    }

    // SUPER_ADMIN인지 확인
    public static boolean isSuperAdmin(LoginUserInfoDto loginUser) {
        return hasRole(loginUser, ROLE_SUPER_ADMIN);
    }

    // 운영 화면에 접근할 수 있는 사용자인지 확인할 때 사용
    public static boolean isOperator(LoginUserInfoDto loginUser) {
        return isCounselor(loginUser) || isSuperAdmin(loginUser);
    }

    // 문의방 생성은 고객만 가능하니까 운영 계정 차단
    public static void validateCustomerOnly(LoginUserInfoDto loginUser) {
        if (!isCustomer(loginUser)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    public static void validateCounselor(LoginUserInfoDto loginUser) {
        if (!isCounselor(loginUser)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    // 운영 전용 API에서 공통으로 사용하는 권한 검증
    public static void validateOperator(LoginUserInfoDto loginUser) {
        if (!isOperator(loginUser)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    // 재배정, 강제 종료처럼 더 강한 운영 권한이 필요한 기능에 사용
    public static void validateSuperAdmin(LoginUserInfoDto loginUser) {
        if (!isSuperAdmin(loginUser)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    // SUPER_ADMIN은 조회는 가능하지만 채팅 참여자처럼 메시지/읽음 처리를 하진 않음
    public static void validateParticipantWritable(LoginUserInfoDto loginUser) {
        if (isSuperAdmin(loginUser)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    // 방 상세/메시지/읽음 처리처럼 이 사용자가 이 방에 들어와도 되는가를 검증
    // SUPER_ADMIN은 전체 조회가 가능하고 상담원과 고객은 자신에게 연결된 방만 접근할 수 있음
    public static void validateRoomAccess(LoginUserInfoDto loginUser, SupportRoom room) {
        if (isSuperAdmin(loginUser)) {
            return;
        }

        if (isCounselor(loginUser)) {
            if (!Objects.equals(room.getCounselorUserId(), loginUser.id())) {
                throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
            }
            return;
        }

        if (!Objects.equals(room.getCustomerUserId(), loginUser.id())) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    // 종료된 문의방은 이력 조회만 가능하고 새 메시지는 받지 않도록 막아서 닫힌 방 상태가 실시간 송신으로 다시 깨지지 않게 함
    public static void validateRoomWritable(SupportRoom room) {
        if (room.getStatus() == SupportRoomStatus.CLOSED) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_ALREADY_CLOSED);
        }
    }

    private static boolean hasRole(LoginUserInfoDto loginUser, String expectedRole) {
        return loginUser != null && Objects.equals(loginUser.role(), expectedRole);
    }
}
