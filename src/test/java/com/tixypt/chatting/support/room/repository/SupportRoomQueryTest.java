package com.tixypt.chatting.support.room.repository;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
@Disabled("조회 성능 실험용 테스트")
public class SupportRoomQueryTest {

    private static final int QUERY_LIMIT = 50;
    private static final int WARM_UP_RUNS = 3;
    private static final int MEASURE_RUNS = 10;

    private static final long EXPERIMENT_CUSTOMER_START = 900_000L;
    private static final long EXPERIMENT_CUSTOMER_END = 900_999L;

    private static final LocalDateTime STALE_CUTOFF = LocalDateTime.of(2026, 4, 1, 12, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void measureSupportRoomQueries() {
        RepresentativeTargets targets = findRepresentativeTargets();

        printDataSummary(targets);
        printIndexSummary();

        List<QueryBenchmarkResult> results = List.of(
                benchmark(
                        "고객 문의방 목록 조회",
                        """
                        select id
                        from support_rooms
                        where customer_user_id = ?
                        order by coalesce(last_message_at, created_at) desc, id desc
                        limit %d
                        """.formatted(QUERY_LIMIT),
                        targets.customerUserId()
                ),
                benchmark(
                        "상담원 담당 문의방 목록 조회",
                        """
                        select id
                        from support_rooms
                        where counselor_user_id = ?
                          and status in ('OPEN', 'SOLVED')
                        order by coalesce(last_message_at, created_at) desc, id desc
                        limit %d
                        """.formatted(QUERY_LIMIT),
                        targets.assignedCounselorUserId()
                ),
                benchmark(
                        "운영 대기열 조회",
                        """
                        select id
                        from support_rooms
                        where counselor_user_id is null
                          and status = 'OPEN'
                        order by
                            case when customer_requested_counselor_at is null then 1 else 0 end asc,
                            customer_requested_counselor_at asc,
                            coalesce(last_message_at, created_at) desc,
                            id desc
                        limit %d
                        """.formatted(QUERY_LIMIT)
                ),
                benchmark(
                        "장기 미응답 방 조회",
                        """
                        select id
                        from support_rooms
                        where status = 'OPEN'
                          and counselor_user_id is not null
                          and counselor_last_active_at is not null
                          and counselor_last_active_at <= ?
                        order by counselor_last_active_at asc, id asc
                        limit %d
                        """.formatted(QUERY_LIMIT),
                        Timestamp.valueOf(STALE_CUTOFF)
                ),
                benchmark(
                        "상담원 종료 이력 조회",
                        """
                        select id
                        from support_rooms
                        where last_counselor_user_id = ?
                          and status = 'CLOSED'
                        order by updated_at desc, id desc
                        limit %d
                        """.formatted(QUERY_LIMIT),
                        targets.closedCounselorUserId()
                )
        );

        System.out.println();
        System.out.println("=== 측정 결과 ===");
        results.forEach(this::printResult);
    }

    private RepresentativeTargets findRepresentativeTargets() {
        Long customerUserId = queryRequiredLong("""
                select customer_user_id
                from support_rooms
                where customer_user_id between ? and ?
                group by customer_user_id
                order by count(*) desc, customer_user_id asc
                limit 1
                """, "대표 customerUserId", EXPERIMENT_CUSTOMER_START, EXPERIMENT_CUSTOMER_END);

        Long assignedCounselorUserId = queryRequiredLong("""
                select counselor_user_id
                from support_rooms
                where customer_user_id between ? and ?
                  and counselor_user_id is not null
                  and status in ('OPEN', 'SOLVED')
                group by counselor_user_id
                order by count(*) desc, counselor_user_id asc
                limit 1
                """, "대표 assigned counselorUserId", EXPERIMENT_CUSTOMER_START, EXPERIMENT_CUSTOMER_END);

        Long closedCounselorUserId = queryRequiredLong("""
                select last_counselor_user_id
                from support_rooms
                where customer_user_id between ? and ?
                  and last_counselor_user_id is not null
                  and status = 'CLOSED'
                group by last_counselor_user_id
                order by count(*) desc, last_counselor_user_id asc
                limit 1
                """, "대표 closed counselorUserId", EXPERIMENT_CUSTOMER_START, EXPERIMENT_CUSTOMER_END);

        return new RepresentativeTargets(customerUserId, assignedCounselorUserId, closedCounselorUserId);
    }

    private Long queryRequiredLong(String sql, String label, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        assertThat(value)
                .as(label + "가 없습니다. 먼저 SupportTestDataGenerator를 실행해 주세요.")
                .isNotNull();
        return value;
    }

    private void printDataSummary(RepresentativeTargets targets) {
        Long experimentRoomCount = jdbcTemplate.queryForObject("""
                select count(*)
                from support_rooms
                where customer_user_id between ? and ?
                """, Long.class, EXPERIMENT_CUSTOMER_START, EXPERIMENT_CUSTOMER_END);

        assertThat(experimentRoomCount)
                .as("실험 데이터가 없습니다. 먼저 SupportTestDataGenerator를 실행해 주세요.")
                .isNotNull()
                .isGreaterThan(0L);

        System.out.println("=== support_rooms 실험 데이터 요약 ===");
        System.out.println("실험용 support_rooms 수: " + String.format("%,d", experimentRoomCount));
        System.out.println("대표 customerUserId: " + targets.customerUserId());
        System.out.println("대표 담당 counselorUserId: " + targets.assignedCounselorUserId());
        System.out.println("대표 종료이력 counselorUserId: " + targets.closedCounselorUserId());
    }

    private void printIndexSummary() {
        System.out.println();
        System.out.println("=== support_rooms 인덱스 정보 ===");

        List<Map<String, Object>> indexRows = jdbcTemplate.queryForList("show index from support_rooms");
        assertThat(indexRows).isNotEmpty();

        indexRows.forEach(row -> System.out.println(
                "keyName=" + row.get("Key_name")
                        + ", columnName=" + row.get("Column_name")
                        + ", seqInIndex=" + row.get("Seq_in_index")
                        + ", cardinality=" + row.get("Cardinality")
        ));
    }

    private QueryBenchmarkResult benchmark(String queryName, String sql, Object... args) {
        for (int i = 0; i < WARM_UP_RUNS; i++) {
            runIdQuery(sql, args);
        }

        List<Long> baselineIds = null;
        long totalNanos = 0L;
        long minNanos = Long.MAX_VALUE;
        long maxNanos = 0L;

        for (int i = 0; i < MEASURE_RUNS; i++) {
            long startedAt = System.nanoTime();
            List<Long> currentIds = runIdQuery(sql, args);
            long elapsed = System.nanoTime() - startedAt;

            if (baselineIds == null) {
                baselineIds = currentIds;
            } else {
                assertThat(currentIds).containsExactlyElementsOf(baselineIds);
            }

            totalNanos += elapsed;
            minNanos = Math.min(minNanos, elapsed);
            maxNanos = Math.max(maxNanos, elapsed);
        }

        assertThat(baselineIds).isNotNull();

        return new QueryBenchmarkResult(
                queryName,
                baselineIds,
                nanosToMillis(totalNanos / (double) MEASURE_RUNS),
                nanosToMillis(minNanos),
                nanosToMillis(maxNanos),
                buildExplain(sql, args)
        );
    }

    private List<Long> runIdQuery(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong(1), args);
    }

    private String buildExplain(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("explain " + sql, args);
        StringBuilder builder = new StringBuilder();

        for (Map<String, Object> row : rows) {
            if (!builder.isEmpty()) {
                builder.append(" | ");
            }

            builder.append(String.format(
                    Locale.ROOT,
                    "type=%s, key=%s, rows=%s, extra=%s",
                    row.get("type"),
                    row.get("key"),
                    row.get("rows"),
                    row.get("Extra")
            ));
        }

        return builder.toString();
    }

    private void printResult(QueryBenchmarkResult result) {
        System.out.println("=== " + result.queryName() + " ===");
        System.out.println("avg=" + round(result.averageMillis()) +
                "ms, min=" + round(result.minMillis()) +
                "ms, max=" + round(result.maxMillis()) + "ms");
        System.out.println("explain=" + result.explainSummary());
        System.out.println("resultCount=" + result.resultIds().size());
        System.out.println("top10Ids=" + result.resultIds().stream().limit(10).toList());
        System.out.println();
    }

    private double nanosToMillis(double nanos) {
        return nanos / 1_000_000.0;
    }

    private String round(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private record RepresentativeTargets(
            Long customerUserId,
            Long assignedCounselorUserId,
            Long closedCounselorUserId
    ) {
    }

    private record QueryBenchmarkResult(
            String queryName,
            List<Long> resultIds,
            double averageMillis,
            double minMillis,
            double maxMillis,
            String explainSummary
    ) {
    }
}
