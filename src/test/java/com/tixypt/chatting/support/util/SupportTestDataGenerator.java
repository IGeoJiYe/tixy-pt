package com.tixypt.chatting.support.util;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@SpringBootTest(properties = {
        "jwt.secret-key=testsecretkeyfortestjwthellotakocuteverymuch1234567890",
        "JWT_SECRET_KEY=testsecretkeyfortestjwthellotakocuteverymuch1234567890",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.ai.model.image=none",
        "spring.ai.model.moderation=none",
        "spring.ai.model.audio.speech=none",
        "spring.ai.model.audio.transcription=none"
})
@ActiveProfiles("test")
@Disabled("데이터 생성/점검/정리용 테스트")
public class SupportTestDataGenerator {

    private static final int ROOM_COUNT = 60_000;
    private static final int CUSTOMER_COUNT = 1_000;
    private static final int COUNSELOR_COUNT = 120;

    private static final long EXPERIMENT_CUSTOMER_START = 900_000L;
    private static final long EXPERIMENT_COUNSELOR_START = 990_000L;

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 4, 1, 9, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void generateSupportRoomData() {
        System.out.println("=== Step 0: 기존 실험 데이터 정리 ===");
        cleanExperimentDataInternal();

        System.out.println("=== Step 1: support_rooms 실험 데이터 생성 ===");
        jdbcTemplate.batchUpdate("""
                        insert into support_rooms (
                            customer_user_id,
                            counselor_user_id,
                            last_counselor_user_id,
                            status,
                            last_message_id,
                            last_message_at,
                            solved_at,
                            customer_last_read_message_id,
                            counselor_last_read_message_id,
                            customer_last_read_at,
                            counselor_last_read_at,
                            counselor_last_active_at,
                            customer_requested_counselor_at,
                            created_at,
                            updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int index) throws SQLException {
                        RoomSeed room = createRoomSeed(index + 1L);

                        ps.setLong(1, room.customerUserId);
                        setNullableLong(ps, 2, room.counselorUserId);
                        setNullableLong(ps, 3, room.lastCounselorUserId);
                        ps.setString(4, room.status);
                        setNullableLong(ps, 5, room.lastMessageId);
                        setNullableDateTime(ps, 6, room.lastMessageAt);
                        setNullableDateTime(ps, 7, room.solvedAt);
                        ps.setNull(8, java.sql.Types.BIGINT);
                        ps.setNull(9, java.sql.Types.BIGINT);
                        ps.setNull(10, java.sql.Types.TIMESTAMP);
                        ps.setNull(11, java.sql.Types.TIMESTAMP);
                        setNullableDateTime(ps, 12, room.counselorLastActiveAt);
                        setNullableDateTime(ps, 13, room.customerRequestedCounselorAt);
                        ps.setTimestamp(14, Timestamp.valueOf(room.createdAt));
                        ps.setTimestamp(15, Timestamp.valueOf(room.updatedAt));
                    }

                    @Override
                    public int getBatchSize() {
                        return ROOM_COUNT;
                    }
                }
        );

        System.out.println("support_rooms 실험 데이터 " + String.format("%,d", ROOM_COUNT) + "건 생성 완료");
        System.out.println();
        checkSupportRoomData();
    }

    @Test
    void checkSupportRoomData() {
        System.out.println("=== support_rooms 실험 데이터 점검 ===");

        Long roomCount = jdbcTemplate.queryForObject("""
                select count(*)
                from support_rooms
                where customer_user_id between ? and ?
                """, Long.class, experimentCustomerStart(), experimentCustomerEnd());

        System.out.println("실험용 support_rooms 수: " + String.format("%,d", roomCount));

        List<Map<String, Object>> statusCounts = jdbcTemplate.queryForList("""
                select status, count(*) as cnt
                from support_rooms
                where customer_user_id between ? and ?
                group by status
                order by status
                """, experimentCustomerStart(), experimentCustomerEnd());

        System.out.println();
        System.out.println("[상태별 건수]");
        statusCounts.forEach(row ->
                System.out.println(row.get("status") + ": " + String.format("%,d", row.get("cnt")))
        );

        Long requestedQueueCount = jdbcTemplate.queryForObject("""
                select count(*)
                from support_rooms
                where customer_user_id between ? and ?
                  and status = 'OPEN'
                  and counselor_user_id is null
                  and customer_requested_counselor_at is not null
                """, Long.class, experimentCustomerStart(), experimentCustomerEnd());

        Long assignedOpenCount = jdbcTemplate.queryForObject("""
                select count(*)
                from support_rooms
                where customer_user_id between ? and ?
                  and status = 'OPEN'
                  and counselor_user_id is not null
                """, Long.class, experimentCustomerStart(), experimentCustomerEnd());

        Long solvedCount = jdbcTemplate.queryForObject("""
                select count(*)
                from support_rooms
                where customer_user_id between ? and ?
                  and status = 'SOLVED'
                """, Long.class, experimentCustomerStart(), experimentCustomerEnd());

        Long closedCount = jdbcTemplate.queryForObject("""
                select count(*)
                from support_rooms
                where customer_user_id between ? and ?
                  and status = 'CLOSED'
                """, Long.class, experimentCustomerStart(), experimentCustomerEnd());

        System.out.println();
        System.out.println("[운영 확인용 요약]");
        System.out.println("상담원 요청 대기 OPEN 방: " + String.format("%,d", requestedQueueCount));
        System.out.println("상담원 배정 OPEN 방: " + String.format("%,d", assignedOpenCount));
        System.out.println("SOLVED 방: " + String.format("%,d", solvedCount));
        System.out.println("CLOSED 방: " + String.format("%,d", closedCount));

        List<Map<String, Object>> queueSamples = jdbcTemplate.queryForList("""
                select id, customer_user_id, customer_requested_counselor_at, created_at
                from support_rooms
                where customer_user_id between ? and ?
                  and counselor_user_id is null
                  and status = 'OPEN'
                order by
                    case when customer_requested_counselor_at is null then 1 else 0 end asc,
                    customer_requested_counselor_at asc,
                    coalesce(last_message_at, created_at) desc,
                    id desc
                limit 5
                """, experimentCustomerStart(), experimentCustomerEnd());

        System.out.println();
        System.out.println("[대기열 샘플 5건]");
        queueSamples.forEach(row -> System.out.println(
                "roomId=" + row.get("id")
                        + ", customerUserId=" + row.get("customer_user_id")
                        + ", requestedAt=" + row.get("customer_requested_counselor_at")
                        + ", createdAt=" + row.get("created_at")
        ));
    }

    @Test
    void cleanSupportRoomData() {
        System.out.println("=== 실험 데이터 정리 시작 ===");
        cleanExperimentDataInternal();
        System.out.println("=== 실험 데이터 정리 완료 ===");
    }

    private void cleanExperimentDataInternal() {
        int deletedMessages = jdbcTemplate.update("""
                delete from support_messages
                where room_id in (
                    select room_id
                    from (
                        select id as room_id
                        from support_rooms
                        where customer_user_id between ? and ?
                    ) rooms
                )
                """, experimentCustomerStart(), experimentCustomerEnd());

        int deletedRooms = jdbcTemplate.update("""
                delete from support_rooms
                where customer_user_id between ? and ?
                """, experimentCustomerStart(), experimentCustomerEnd());

        System.out.println("삭제된 support_messages 수: " + String.format("%,d", deletedMessages));
        System.out.println("삭제된 support_rooms 수: " + String.format("%,d", deletedRooms));
    }

    private RoomSeed createRoomSeed(long sequence) {
        long customerUserId = experimentCustomerStart() + ((sequence - 1) % CUSTOMER_COUNT);
        String status = resolveStatus(sequence);

        LocalDateTime createdAt = BASE_TIME.plusSeconds(sequence);
        LocalDateTime lastMessageAt = sequence % 9 == 0
                ? null
                : createdAt.plusMinutes((sequence % 240) + 1);
        LocalDateTime updatedAt = lastMessageAt != null
                ? lastMessageAt.plusMinutes(1)
                : createdAt.plusMinutes(2);
        Long lastMessageId = lastMessageAt == null ? null : sequence;

        if ("CLOSED".equals(status)) {
            long lastCounselorUserId = experimentCounselorStart() + ((sequence - 1) % COUNSELOR_COUNT);
            return new RoomSeed(
                    customerUserId,
                    null,
                    lastCounselorUserId,
                    status,
                    lastMessageId,
                    lastMessageAt,
                    null,
                    null,
                    createdAt.plusMinutes(30),
                    createdAt,
                    updatedAt.plusMinutes(1)
            );
        }

        if ("SOLVED".equals(status)) {
            long counselorUserId = experimentCounselorStart() + ((sequence - 1) % COUNSELOR_COUNT);
            return new RoomSeed(
                    customerUserId,
                    counselorUserId,
                    counselorUserId,
                    status,
                    lastMessageId,
                    lastMessageAt,
                    lastMessageAt == null ? null : lastMessageAt.minusMinutes(5),
                    createdAt.plusMinutes(20),
                    createdAt.plusMinutes(20),
                    createdAt,
                    updatedAt
            );
        }

        boolean assigned = sequence % 4 != 0;
        Long counselorUserId = assigned
                ? experimentCounselorStart() + ((sequence - 1) % COUNSELOR_COUNT)
                : null;
        Long lastCounselorUserId = assigned
                ? counselorUserId
                : (sequence % 6 == 0 ? experimentCounselorStart() + ((sequence - 1) % COUNSELOR_COUNT) : null);
        LocalDateTime requestedAt = assigned || sequence % 8 != 0
                ? null
                : createdAt.plusMinutes((sequence % 90) + 5);
        LocalDateTime counselorLastActiveAt = assigned
                ? createdAt.plusMinutes(sequence % 360)
                : null;

        return new RoomSeed(
                customerUserId,
                counselorUserId,
                lastCounselorUserId,
                status,
                lastMessageId,
                lastMessageAt,
                requestedAt,
                counselorLastActiveAt,
                null,
                createdAt,
                updatedAt
        );
    }

    private String resolveStatus(long sequence) {
        int bucket = (int) (sequence % 10);

        if (bucket == 0 || bucket == 1) {
            return "CLOSED";
        }

        if (bucket == 2 || bucket == 3) {
            return "SOLVED";
        }

        return "OPEN";
    }

    private long experimentCustomerStart() {
        return EXPERIMENT_CUSTOMER_START;
    }

    private long experimentCustomerEnd() {
        return EXPERIMENT_CUSTOMER_START + CUSTOMER_COUNT - 1;
    }

    private long experimentCounselorStart() {
        return EXPERIMENT_COUNSELOR_START;
    }

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.BIGINT);
            return;
        }
        ps.setLong(index, value);
    }

    private void setNullableDateTime(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.TIMESTAMP);
            return;
        }
        ps.setTimestamp(index, Timestamp.valueOf(value));
    }

    private static class RoomSeed {
        private final long customerUserId;
        private final Long counselorUserId;
        private final Long lastCounselorUserId;
        private final String status;
        private final Long lastMessageId;
        private final LocalDateTime lastMessageAt;
        private final LocalDateTime customerRequestedCounselorAt;
        private final LocalDateTime counselorLastActiveAt;
        private final LocalDateTime solvedAt;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;

        private RoomSeed(
                long customerUserId,
                Long counselorUserId,
                Long lastCounselorUserId,
                String status,
                Long lastMessageId,
                LocalDateTime lastMessageAt,
                LocalDateTime customerRequestedCounselorAt,
                LocalDateTime counselorLastActiveAt,
                LocalDateTime solvedAt,
                LocalDateTime createdAt,
                LocalDateTime updatedAt
        ) {
            this.customerUserId = customerUserId;
            this.counselorUserId = counselorUserId;
            this.lastCounselorUserId = lastCounselorUserId;
            this.status = status;
            this.lastMessageId = lastMessageId;
            this.lastMessageAt = lastMessageAt;
            this.customerRequestedCounselorAt = customerRequestedCounselorAt;
            this.counselorLastActiveAt = counselorLastActiveAt;
            this.solvedAt = solvedAt;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }
}
