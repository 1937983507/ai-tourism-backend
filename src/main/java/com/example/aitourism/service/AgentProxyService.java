package com.example.aitourism.service;

import reactor.core.publisher.Flux;

/**
 * Agent 代理服务
 * 负责与 Python Agent 服务通信
 */
public interface AgentProxyService {

    /**
     * 转发流式对话请求到 Python Agent 服务
     *
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param message 用户消息
     * @return SSE 流式响应（字符串流）
     */
    Flux<String> chatStream(String sessionId, String userId, String message);
}
