package com.tixypt.chatting.support.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

//@Disabled("로컬 MySQL에서만 수동 실행")
class SupportIndexBenchmarkTest {

    private static final String ROOM_NO_INDEX = "bench_support_rooms_no_index";
    private static final String ROOM_LIST_OPTIMIZED = "bench_support_rooms_list_optimized";
    private static final String ROOM_OPERATION_OPTIMIZED = "bench_support_rooms_operation_optimized";
    private static final String ROOM_HEAVY = "bench_support_rooms_heavy";

    private static final String MESSAGE_NO_INDEX = "bench_support_messages_no_index";
    private static final String MESSAGE_MINIMAL = "bench_support_messages_minimal";
    private static final String MESSAGE_FALLBACK = "bench_support_messages_fallback";
    private static final String MESSAGE_HEAVY = "bench_support_messages_heavy";

    private static final int ROOM_ROWS = 50_000;
    private static final int MESSAGE_ROWS = 50_000;
    private static final int MESSAGE_ROOM_COUNT = 500;
    private static final int READ_RUNS = 5;
    private static final int INSERT_RUNS = 3;
    private static final int ROOM_UPDATE_RUNS = 8_000;
    private static final int MESSAGE_INSERTS = 3_000;
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 4, 1, 9, 0);
    private static final String BASE_TIME_SQL = "2026-04-01 09:00:00";

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(System.getProperty("benchmark.db.url", "jdbc:mysql://localhost:3307/tixy"));
        dataSource.setUsername(System.getProperty("benchmark.db.username", "root"));
        dataSource.setPassword(System.getProperty("benchmark.db.password", "12345678"));
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("support_rooms 인덱스 후보별 읽기와 쓰기 성능을 비교한다")
    void compareSupportRoomIndexCandidates() {
        List<BenchmarkTable> tables = roomTables();
        prepareRoomTables();

        try {
            long customerUserId = 810_000L + 17;
            long counselorUserId = 910_000L + 7;
            Timestamp staleCutoff = Timestamp.valueOf(BASE_TIME.plusMinutes(240));

            List<ScenarioResult> readResults = List.of(
                    compareSelect("고객 문의방 목록", tables, """
                            select id from %s
                            where customer_user_id = ?
                            order by last_message_at desc, id desc
                            limit 50
                            """, customerUserId),
                    compareSelect("상담사 배정 방 목록", tables, """
                            select id from %s
                            where counselor_user_id = ?
                              and status in ('OPEN', 'SOLVED')
                            order by last_message_at desc, id desc
                            limit 50
                            """, counselorUserId),
                    compareSelect("운영 큐 목록", tables, """
                            select id from %s
                            where counselor_user_id is null
                              and status = 'OPEN'
                            order by
                                case when customer_requested_counselor_at is null then 1 else 0 end asc,
                                customer_requested_counselor_at asc,
                                last_message_at desc,
                                id desc
                            limit 50
                            """),
                    compareSelect("응답 지연 방 목록", tables, """
                            select id from %s
                            where status = 'OPEN'
                              and counselor_user_id is not null
                              and counselor_last_active_at is not null
                              and counselor_last_active_at <= ?
                            order by counselor_last_active_at asc, id asc
                            limit 50
                            """, staleCutoff),
                    compareSelect("종료 이력 목록", tables, """
                            select id from %s
                            where last_counselor_user_id = ?
                              and status = 'CLOSED'
                            order by updated_at desc, id desc
                            limit 50
                            """, counselorUserId)
            );

            List<ScenarioResult> writeResults = List.of(
                    compareUpdate("마지막 메시지 갱신", tables, """
                            update %s
                            set last_message_id = ?,
                                last_message_at = ?,
                                updated_at = ?
                            where id = ?
                            """, index -> {
                        LocalDateTime changedAt = BASE_TIME.plusDays(30).plusSeconds(index);
                        return new Object[]{
                                2_000_000L + index,
                                Timestamp.valueOf(changedAt),
                                Timestamp.valueOf(changedAt),
                                (index % ROOM_ROWS) + 1L
                        };
                    }),
                    compareUpdate("상담사 활동시각 갱신", tables, """
                            update %s
                            set counselor_last_active_at = ?,
                                updated_at = ?
                            where id = ?
                            """, index -> {
                        LocalDateTime changedAt = BASE_TIME.plusDays(40).plusSeconds(index);
                        return new Object[]{
                                Timestamp.valueOf(changedAt),
                                Timestamp.valueOf(changedAt),
                                ((index * 3L) % ROOM_ROWS) + 1L
                        };
                    }),
                    compareUpdate("상담사 배정 처리", tables, """
                            update %s
                            set counselor_user_id = ?,
                                counselor_last_active_at = ?,
                                customer_requested_counselor_at = null,
                                updated_at = ?
                            where id = ?
                              and counselor_user_id is null
                              and status = 'OPEN'
                            """, index -> {
                        LocalDateTime changedAt = BASE_TIME.plusDays(50).plusSeconds(index);
                        return new Object[]{
                                950_000L + (index % 50),
                                Timestamp.valueOf(changedAt),
                                Timestamp.valueOf(changedAt),
                                ((index * 5L) % ROOM_ROWS) + 1L
                        };
                    })
            );

            printMarkdownReport("support_rooms 읽기 성능", tables, readResults);
            printExplainReport("support_rooms 읽기 EXPLAIN", readResults);
            printMarkdownReport("support_rooms 쓰기 성능", tables, writeResults);
        } finally {
            dropTables(tables);
        }
    }

    @Test
    @DisplayName("support_messages 인덱스 후보별 읽기와 쓰기 성능을 비교한다")
    void compareSupportMessageIndexCandidates() {
        List<BenchmarkTable> tables = messageTables();
        prepareMessageTables(MESSAGE_ROWS);

        try {
            long roomId = 321L;
            long latestMessageId = roomId + (MESSAGE_ROWS / MESSAGE_ROOM_COUNT - 1L) * MESSAGE_ROOM_COUNT;
            long beforeMessageId = latestMessageId - MESSAGE_ROOM_COUNT;
            long customerUserId = 700_000L + roomId;
            long counselorUserId = 710_000L + (roomId % 300L);

            List<ScenarioResult> readResults = List.of(
                    compareSelect("최신 메시지 50개", tables, """
                            select id from %s
                            where room_id = ?
                            order by id desc
                            limit 50
                            """, roomId),
                    compareSelect("커서 기반 이전 메시지 50개", tables, """
                            select id from %s
                            where room_id = ?
                              and id < ?
                            order by id desc
                            limit 50
                            """, roomId, beforeMessageId),
                    compareSelect("AI fallback 최신 USER 메시지", tables, """
                            select id from %s
                            where room_id = ?
                              and sender_type = 'USER'
                            order by id desc
                            limit 1
                            """, roomId),
                    compareSelect("고객 unread count", tables, """
                            select count(*) from %s
                            where room_id = ?
                              and message_type <> 'SYSTEM'
                              and id > ?
                              and (sender_type <> 'USER' or sender_user_id is null or sender_user_id <> ?)
                            """, roomId, latestMessageId - (MESSAGE_ROOM_COUNT * 4L), customerUserId),
                    compareSelect("상담사 unread count", tables, """
                            select count(*) from %s
                            where room_id = ?
                              and message_type <> 'SYSTEM'
                              and id > ?
                              and (sender_type <> 'COUNSELOR' or sender_user_id is null or sender_user_id <> ?)
                            """, roomId, latestMessageId - (MESSAGE_ROOM_COUNT * 4L), counselorUserId)
            );

            ScenarioResult writeResult = compareMessageInsert("메시지 INSERT", tables);

            printMarkdownReport("support_messages 읽기 성능", tables, readResults);
            printExplainReport("support_messages 읽기 EXPLAIN", readResults);
            printMarkdownReport("support_messages 쓰기 성능", tables, List.of(writeResult));
        } finally {
            dropTables(tables);
        }
    }

    private List<BenchmarkTable> roomTables() {
        return List.of(
                new BenchmarkTable("no-index", ROOM_NO_INDEX),
                new BenchmarkTable("목록 최적화", ROOM_LIST_OPTIMIZED),
                new BenchmarkTable("운영 최적화", ROOM_OPERATION_OPTIMIZED),
                new BenchmarkTable("heavy", ROOM_HEAVY)
        );
    }

    private List<BenchmarkTable> messageTables() {
        return List.of(
                new BenchmarkTable("no-index", MESSAGE_NO_INDEX),
                new BenchmarkTable("최소 인덱스", MESSAGE_MINIMAL),
                new BenchmarkTable("fallback 포함", MESSAGE_FALLBACK),
                new BenchmarkTable("heavy", MESSAGE_HEAVY)
        );
    }

    private void prepareRoomTables() {
        List<BenchmarkTable> tables = roomTables();
        dropTables(tables);
        tables.forEach(table -> createRoomTable(table.tableName()));
        createListOptimizedRoomIndexes(ROOM_LIST_OPTIMIZED);
        createOperationOptimizedRoomIndexes(ROOM_OPERATION_OPTIMIZED);
        createHeavyRoomIndexes(ROOM_HEAVY);
        tables.forEach(table -> insertRooms(table.tableName(), ROOM_ROWS));
    }

    private void prepareMessageTables(int rowCount) {
        List<BenchmarkTable> tables = messageTables();
        dropTables(tables);
        tables.forEach(table -> createMessageTable(table.tableName()));
        createMinimalMessageIndexes(MESSAGE_MINIMAL);
        createFallbackMessageIndexes(MESSAGE_FALLBACK);
        createHeavyMessageIndexes(MESSAGE_HEAVY);
        tables.forEach(table -> insertMessages(table.tableName(), 1, rowCount));
    }

    private ScenarioResult compareSelect(String scenario, List<BenchmarkTable> tables, String sql, Object... args) {
        List<Measurement> measurements = new ArrayList<>();

        for (BenchmarkTable table : tables) {
            String concreteSql = sql.formatted(table.tableName());
            double millis = selectAvg(concreteSql, args);
            String explain = explain(concreteSql, args);
            measurements.add(new Measurement(table.label(), millis, explain));
        }

        return new ScenarioResult(scenario, measurements);
    }

    private ScenarioResult compareUpdate(String scenario, List<BenchmarkTable> tables, String sql, Args args) {
        List<Measurement> measurements = new ArrayList<>();

        for (BenchmarkTable table : tables) {
            double millis = updateAvg(sql.formatted(table.tableName()), args);
            measurements.add(new Measurement(table.label(), millis, null));
        }

        return new ScenarioResult(scenario, measurements);
    }

    private ScenarioResult compareMessageInsert(String scenario, List<BenchmarkTable> tables) {
        Map<String, Double> totals = new LinkedHashMap<>();
        tables.forEach(table -> totals.put(table.label(), 0.0d));

        for (int round = 0; round < INSERT_RUNS; round++) {
            prepareMessageTables(MESSAGE_ROWS);
            for (BenchmarkTable table : rotateTables(tables, round)) {
                double millis = insertAvg(
                        table.tableName(),
                        MESSAGE_ROWS + 1,
                        MESSAGE_ROWS + MESSAGE_INSERTS
                );
                totals.compute(table.label(), (key, value) -> value + millis);
            }
        }

        List<Measurement> measurements = new ArrayList<>();
        for (BenchmarkTable table : tables) {
            measurements.add(new Measurement(
                    table.label(),
                    totals.get(table.label()) / INSERT_RUNS,
                    null
            ));
        }

        return new ScenarioResult(scenario, measurements);
    }

    private List<BenchmarkTable> rotateTables(List<BenchmarkTable> tables, int round) {
        List<BenchmarkTable> rotated = new ArrayList<>(tables);
        Collections.rotate(rotated, -(round % rotated.size()));
        return rotated;
    }

    private void printMarkdownReport(String title, List<BenchmarkTable> tables, List<ScenarioResult> results) {
        System.out.println();
        System.out.println("### " + title);
        System.out.println("> 괄호 안 변화율은 no-index 대비 값이며, 음수일수록 더 빠릅니다.");
        System.out.print("| 시나리오 |");
        tables.forEach(table -> System.out.print(" " + table.label() + " |"));
        System.out.println();
        System.out.print("| --- |");
        tables.forEach(ignored -> System.out.print(" ---: |"));
        System.out.println();

        for (ScenarioResult result : results) {
            System.out.print("| " + result.scenario() + " |");
            double baseline = result.measurements().getFirst().millis();

            for (int i = 0; i < result.measurements().size(); i++) {
                Measurement measurement = result.measurements().get(i);
                System.out.print(" " + formatCell(baseline, measurement.millis(), i == 0) + " |");
            }
            System.out.println();
        }
    }

    private void printExplainReport(String title, List<ScenarioResult> results) {
        System.out.println();
        System.out.println("### " + title);
        for (ScenarioResult result : results) {
            System.out.println("- " + result.scenario());
            for (Measurement measurement : result.measurements()) {
                System.out.println("  - " + measurement.label() + ": " + measurement.explain());
            }
        }
    }

    private String formatCell(double baseline, double value, boolean baselineCell) {
        if (baselineCell) {
            return String.format(Locale.ROOT, "%.3fms", value);
        }
        return String.format(Locale.ROOT, "%.3fms (%s)", value, diffLabel(baseline, value));
    }

    private String diffLabel(double baseline, double value) {
        if (baseline == 0.0d) {
            return "-";
        }
        double diff = ((value - baseline) / baseline) * 100.0d;
        return String.format(Locale.ROOT, "%+.2f%%", diff);
    }

    private double selectAvg(String sql, Object... args) {
        jdbcTemplate.queryForList(sql, args);

        long total = 0L;
        for (int i = 0; i < READ_RUNS; i++) {
            long startedAt = System.nanoTime();
            jdbcTemplate.queryForList(sql, args);
            total += System.nanoTime() - startedAt;
        }
        return toMillis(total / (double) READ_RUNS);
    }

    private double updateAvg(String sql, Args args) {
        long total = 0L;
        for (int i = 1; i <= ROOM_UPDATE_RUNS; i++) {
            long startedAt = System.nanoTime();
            jdbcTemplate.update(sql, args.create(i));
            total += System.nanoTime() - startedAt;
        }
        return toMillis(total / (double) ROOM_UPDATE_RUNS);
    }

    private double insertAvg(String table, int start, int end) {
        long startedAt = System.nanoTime();
        insertMessages(table, start, end);
        return toMillis(System.nanoTime() - startedAt);
    }

    private void createRoomTable(String table) {
        jdbcTemplate.execute("""
                create table %s (
                    id bigint not null auto_increment primary key,
                    customer_user_id bigint not null,
                    counselor_user_id bigint null,
                    last_counselor_user_id bigint null,
                    status varchar(20) not null,
                    last_message_id bigint null,
                    last_message_at datetime(6) null,
                    solved_at datetime(6) null,
                    customer_last_read_message_id bigint null,
                    counselor_last_read_message_id bigint null,
                    customer_last_read_at datetime(6) null,
                    counselor_last_read_at datetime(6) null,
                    counselor_last_active_at datetime(6) null,
                    customer_requested_counselor_at datetime(6) null,
                    created_at datetime(6) not null,
                    updated_at datetime(6) not null
                )
                """.formatted(table));
    }

    private void createMessageTable(String table) {
        jdbcTemplate.execute("""
                create table %s (
                    id bigint not null auto_increment primary key,
                    room_id bigint not null,
                    sender_user_id bigint null,
                    sender_type varchar(20) not null,
                    message_type varchar(20) not null,
                    content text not null,
                    created_at datetime(6) not null
                )
                """.formatted(table));
    }

    private void createListOptimizedRoomIndexes(String table) {
        jdbcTemplate.execute("create index idx_customer_last_message_id on %s (customer_user_id, last_message_at desc, id desc)".formatted(table));
        jdbcTemplate.execute("create index idx_counselor_status_last_message_id on %s (counselor_user_id, status, last_message_at desc, id desc)".formatted(table));
    }

    private void createOperationOptimizedRoomIndexes(String table) {
        createListOptimizedRoomIndexes(table);
        jdbcTemplate.execute("create index idx_queue_request_id on %s (status, counselor_user_id, customer_requested_counselor_at, id)".formatted(table));
        jdbcTemplate.execute("create index idx_status_counselor_active_id on %s (status, counselor_last_active_at, id)".formatted(table));
        jdbcTemplate.execute("create index idx_last_counselor_status_id on %s (last_counselor_user_id, status, id)".formatted(table));
        jdbcTemplate.execute("create index idx_status_solved_at_id on %s (status, solved_at, id)".formatted(table));
    }

    private void createHeavyRoomIndexes(String table) {
        createOperationOptimizedRoomIndexes(table);
        jdbcTemplate.execute("create index idx_customer_status_id on %s (customer_user_id, status, id)".formatted(table));
        jdbcTemplate.execute("create index idx_counselor_status_id on %s (counselor_user_id, status, id)".formatted(table));
        jdbcTemplate.execute("create index idx_status_last_message_id on %s (status, last_message_at desc, id desc)".formatted(table));
    }

    private void createMinimalMessageIndexes(String table) {
        jdbcTemplate.execute("create index idx_room_id_id on %s (room_id, id)".formatted(table));
    }

    private void createFallbackMessageIndexes(String table) {
        createMinimalMessageIndexes(table);
        jdbcTemplate.execute("create index idx_room_id_sender_type_id on %s (room_id, sender_type, id)".formatted(table));
    }

    private void createHeavyMessageIndexes(String table) {
        createFallbackMessageIndexes(table);
        jdbcTemplate.execute("create index idx_room_id_message_type_id on %s (room_id, message_type, id)".formatted(table));
        jdbcTemplate.execute("create index idx_room_id_created_at_id on %s (room_id, created_at, id)".formatted(table));
        jdbcTemplate.execute("create index idx_sender_user_id_created_at_id on %s (sender_user_id, created_at, id)".formatted(table));
    }

    private void insertRooms(String table, int rowCount) {
        jdbcTemplate.execute("""
                insert into %s (
                    customer_user_id, counselor_user_id, last_counselor_user_id, status,
                    last_message_id, last_message_at, solved_at,
                    customer_last_read_message_id, counselor_last_read_message_id,
                    customer_last_read_at, counselor_last_read_at, counselor_last_active_at,
                    customer_requested_counselor_at, created_at, updated_at
                )
                select
                    810000 + ((n - 1) %% 500),
                    case when n %% 10 in (0, 1) then null when n %% 4 <> 0 then 910000 + ((n - 1) %% 80) else null end,
                    case when n %% 10 in (0, 1) or n %% 4 <> 0 then 910000 + ((n - 1) %% 80) else null end,
                    case when n %% 10 in (0, 1) then 'CLOSED' when n %% 10 in (2, 3) then 'SOLVED' else 'OPEN' end,
                    1000000 + n,
                    date_add(date_add('%s', interval n second), interval (n %% 180) + 1 minute),
                    case when n %% 10 in (0, 1, 2, 3) then date_add(date_add('%s', interval n second), interval (n %% 180) - 10 minute) else null end,
                    1000000 + n - 3,
                    case when n %% 10 not in (0, 1) and n %% 4 <> 0 then 1000000 + n - 1 else null end,
                    date_add(date_add('%s', interval n second), interval (n %% 180) - 4 minute),
                    case when n %% 10 not in (0, 1) and n %% 4 <> 0 then date_add(date_add('%s', interval n second), interval (n %% 180) - 1 minute) else null end,
                    case when n %% 10 not in (0, 1) and n %% 4 <> 0 then date_add(date_add('%s', interval n second), interval -(n %% 120) second) else null end,
                    case when n %% 10 not in (0, 1, 2, 3) and n %% 4 = 0 then date_add(date_add('%s', interval n second), interval (n %% 45) + 2 minute) else null end,
                    date_add('%s', interval n second),
                    date_add(date_add('%s', interval n second), interval (n %% 180) + 2 minute)
                from (%s) seq
                """.formatted(
                table,
                BASE_TIME_SQL,
                BASE_TIME_SQL,
                BASE_TIME_SQL,
                BASE_TIME_SQL,
                BASE_TIME_SQL,
                BASE_TIME_SQL,
                BASE_TIME_SQL,
                BASE_TIME_SQL,
                numbers(1, rowCount)
        ));
    }

    private void insertMessages(String table, int start, int end) {
        jdbcTemplate.execute("""
                insert into %s (room_id, sender_user_id, sender_type, message_type, content, created_at)
                select
                    ((n - 1) %% %d) + 1,
                    case
                        when n %% 6 = 0 then null
                        when n %% 6 in (1, 2, 5) then 700000 + (((n - 1) %% %d) + 1)
                        else 710000 + ((((n - 1) %% %d) + 1) %% 300)
                    end,
                    case when n %% 6 = 0 then 'SYSTEM' when n %% 6 in (1, 2, 5) then 'USER' else 'COUNSELOR' end,
                    case when n %% 6 = 0 then 'SYSTEM' else 'TEXT' end,
                    concat('benchmark-message-', n),
                    date_add('%s', interval n second)
                from (%s) seq
                """.formatted(table, MESSAGE_ROOM_COUNT, MESSAGE_ROOM_COUNT, MESSAGE_ROOM_COUNT, BASE_TIME_SQL, numbers(start, end)));
    }

    private String numbers(int start, int end) {
        return """
                select n
                from (
                    select %d + d0.n + d1.n * 10 + d2.n * 100 + d3.n * 1000 + d4.n * 10000 as n
                    from %s d0
                    cross join %s d1
                    cross join %s d2
                    cross join %s d3
                    cross join %s d4
                ) numbers
                where n <= %d
                """.formatted(start, digits(), digits(), digits(), digits(), digits(), end);
    }

    private String digits() {
        return """
                (select 0 n union all select 1 union all select 2 union all select 3 union all select 4
                 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9)
                """;
    }

    private String explain(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("explain " + sql, args);
        Map<String, Object> row = rows.getFirst();
        return "type=%s, key=%s, rows=%s, extra=%s".formatted(
                row.get("type"),
                row.get("key"),
                row.get("rows"),
                row.get("Extra")
        );
    }

    private void dropTables(List<BenchmarkTable> tables) {
        tables.forEach(table -> jdbcTemplate.execute("drop table if exists " + table.tableName()));
    }

    private double toMillis(double nanos) {
        return nanos / 1_000_000.0d;
    }

    @FunctionalInterface
    private interface Args {
        Object[] create(int index);
    }

    private record BenchmarkTable(String label, String tableName) {
    }

    private record Measurement(String label, double millis, String explain) {
    }

    private record ScenarioResult(String scenario, List<Measurement> measurements) {
    }
}
