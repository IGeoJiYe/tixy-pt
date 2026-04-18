package com.tixypt.chatting.support.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// 문의 채팅 AI 비동기 실행기
// 고객 메시지 저장이랑 ai 호출을 같은 요청 흐름에 묶으면 db 잠금이랑 외부 ai 응답 대기가 한 트랜잭션 안에 섞여서
// 메시지 저장 먼저 끝내고 그 이후에 별도에서 이어서 처리
@Configuration
@EnableAsync
public class AiAsyncConfig {

    @Bean(name = "supportAiExecutor")
    public Executor supportAiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("support-ai-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
