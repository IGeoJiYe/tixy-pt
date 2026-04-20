package com.tixypt.chatting.support.room.service;

import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RoomCreationLockServiceTest {

    private static final String LOCK_KEY_PREFIX = "support:room:create:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RoomCreationLockService roomCreationLockService;
    private ConcurrentHashMap<String, String> locks;

    @BeforeEach
    void setUp() {
        roomCreationLockService = new RoomCreationLockService(redisTemplate);
        locks = new ConcurrentHashMap<>();

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .willAnswer(invocation -> {
                    String lockKey = invocation.getArgument(0);
                    String lockValue = invocation.getArgument(1);
                    return locks.putIfAbsent(lockKey, lockValue) == null;
                });

        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString()))
                .thenAnswer(invocation -> {
                    List<String> keys = invocation.getArgument(1);
                    String lockValue = invocation.getArgument(2);
                    return locks.remove(keys.get(0), lockValue) ? 1L : 0L;
                });
    }

    @Test
    @DisplayName("락 획득에 성공하면 작업을 실행하고 락을 해제한다")
    void executesActionAndReleasesLockWhenLockIsAcquired() {
        String result = roomCreationLockService.withCustomerRoomCreationLock(7L, () -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(locks).isEmpty();

        then(valueOperations).should()
                .setIfAbsent(eq(LOCK_KEY_PREFIX + 7L), anyString(), eq(LOCK_TTL));
        then(redisTemplate).should()
                .execute(any(DefaultRedisScript.class), eq(List.of(LOCK_KEY_PREFIX + 7L)), anyString());
    }

    @Test
    @DisplayName("이미 같은 고객의 락이 존재하면 중복 생성 예외를 던진다")
    void throwsExceptionWhenLockAlreadyExists() {
        locks.put(LOCK_KEY_PREFIX + 11L, "lock:existing");

        assertThatThrownBy(() -> roomCreationLockService.withCustomerRoomCreationLock(11L, () -> "fail"))
                .isInstanceOf(SupportRoomException.class)
                .extracting(throwable -> ((SupportRoomException) throwable).getErrorCode())
                .isEqualTo(SupportRoomErrorCode.ROOM_CREATION_IN_PROGRESS);

        assertThat(locks).containsKey(LOCK_KEY_PREFIX + 11L);
    }

    @Test
    @DisplayName("작업 중 예외가 발생해도 락은 해제된다")
    void releasesLockEvenIfActionThrows() {
        assertThatThrownBy(() -> roomCreationLockService.withCustomerRoomCreationLock(15L, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(locks).doesNotContainKey(LOCK_KEY_PREFIX + 15L);
    }

    @Test
    @DisplayName("동시에 같은 고객의 문의방 생성을 요청하면 하나만 성공한다")
    void allowsOnlyOneSuccessUnderConcurrentRequests() throws Exception {
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger actionCount = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        barrier.await();
                        roomCreationLockService.withCustomerRoomCreationLock(99L, () -> {
                            actionCount.incrementAndGet();
                            sleepSilently(200);
                            return "ok";
                        });
                        successCount.incrementAndGet();
                    } catch (SupportRoomException exception) {
                        if (exception.getErrorCode() == SupportRoomErrorCode.ROOM_CREATION_IN_PROGRESS) {
                            conflictCount.incrementAndGet();
                        } else {
                            unexpectedErrors.add(exception);
                        }
                    } catch (Exception exception) {
                        unexpectedErrors.add(exception);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();

            assertThat(unexpectedErrors).isEmpty();
            assertThat(successCount).hasValue(1);
            assertThat(conflictCount).hasValue(threadCount - 1);
            assertThat(actionCount).hasValue(1);
            assertThat(locks).doesNotContainKey(LOCK_KEY_PREFIX + 99L);
        } finally {
            executor.shutdownNow();
        }
    }

    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}