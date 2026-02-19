package com.example.aitourism.service.impl;

import com.example.aitourism.service.AgentProxyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent 代理服务实现
 * 负责与 Python Agent 服务通信
 */
@Service
@Slf4j
public class AgentProxyServiceImpl implements AgentProxyService {

    @Value("${agent.base-url:http://localhost:8291}")
    private String agentBaseUrl;

    @Value("${agent.internal-token:}")
    private String internalToken;

    private final WebClient webClient;

    public AgentProxyServiceImpl() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }

    @Override
    public Flux<String> chatStream(String sessionId, String userId, String message) {
        log.info("转发流式对话请求到Agent服务，sessionId: {}, userId: {}", sessionId, userId);

        String url = agentBaseUrl + "/agent/chat-stream";

        // 构建请求体
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("session_id", sessionId);
        requestBody.put("user_id", userId);
        requestBody.put("message", message);

        // 构建请求头（可选内部 Token）
        WebClient.RequestBodySpec request = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (internalToken != null && !internalToken.isEmpty()) {
            request = request.header("Authorization", "Bearer " + internalToken);
        }

        // 调用 Python Agent 服务，直接返回字符串流（SSE 格式）
        return request
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofMinutes(5))
                .onErrorResume(error -> {
                    log.error("调用Agent服务失败: {}", error.getMessage(), error);
                    String errorMsg = String.format(
                            "data: {\"choices\":[{\"index\":0,\"text\":\"%s\",\"finish_reason\":\"stop\",\"model\":\"gpt-4o-mini\"}]}\n\n",
                            "Agent服务暂时不可用，请稍后重试"
                    );
                    return Flux.just(errorMsg);
                });
    }
}


