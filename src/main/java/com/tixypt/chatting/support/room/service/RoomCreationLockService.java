package com.tixypt.chatting.support.room.service;

import com.tixypt.chatting.support.exception.SupportRoomErrorCode;
import com.tixypt.chatting.support.exception.SupportRoomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class RoomCreationLockService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final String ROOM_CREATION_LOCK_KEY_PREFIX = "support:room:create:";
    private static final String LOCK_VALUE_PREFIX = "lock:";
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = createReleaseLockScript();

    private final StringRedisTemplate redisTemplate;

    public <T> T withCustomerRoomCreationLock(Long customerUserId, Supplier<T> action) {
        String lockKey = ROOM_CREATION_LOCK_KEY_PREFIX + customerUserId;
        String lockValue = LOCK_VALUE_PREFIX + UUID.randomUUID();

        boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new SupportRoomException(SupportRoomErrorCode.ROOM_CREATION_IN_PROGRESS);
        }

        try {
            return action.get();
        } finally {
            redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), lockValue);
        }
    }

    private static DefaultRedisScript<Long> createReleaseLockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                end
                return 0
                """);
        script.setResultType(Long.class);
        return script;
    }
}
