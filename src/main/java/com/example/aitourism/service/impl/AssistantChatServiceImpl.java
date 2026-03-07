package com.example.aitourism.service.impl;

import com.example.aitourism.dto.chat.ChatHistoryDTO;
import com.example.aitourism.dto.chat.ChatHistoryResponse;
import com.example.aitourism.dto.chat.SessionDTO;
import com.example.aitourism.dto.chat.SessionListResponse;
import com.example.aitourism.entity.Message;
import com.example.aitourism.entity.Session;
import com.example.aitourism.exception.InputValidationException;
import com.example.aitourism.mapper.ChatMessageMapper;
import com.example.aitourism.mapper.SessionMapper;
import com.example.aitourism.service.AgentProxyService;
import com.example.aitourism.service.AssistantChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * AI 助手对话服务实现
 * 职责：
 * 1. 维护会话与消息的持久化
 * 2. 将对话请求转发给 Python Agent
 * 3. 使用小模型生成会话标题
 */
@Service
@Slf4j
public class AssistantChatServiceImpl implements AssistantChatService {

    private final ChatMessageMapper chatMessageMapper;
    private final SessionMapper sessionMapper;
    private final AgentProxyService agentProxyService;
    private final ObjectMapper objectMapper;

    public AssistantChatServiceImpl(
            ChatMessageMapper chatMessageMapper,
            SessionMapper sessionMapper,
            AgentProxyService agentProxyService,
            ObjectMapper objectMapper
    ) {
        this.chatMessageMapper = chatMessageMapper;
        this.sessionMapper = sessionMapper;
        this.agentProxyService = agentProxyService;
        this.objectMapper = objectMapper;
    }

    // LLM相关配置（用于生成会话标题）
    @Value("${openai.api-key}")
    private String apiKey;
    @Value("${openai.base-url}")
    private String baseUrl;
    @Value("${openai.model-name}")
    private String modelName;

    // 对话请求（Reactor 流式）- 转发到 Python Agent 服务
    @Override
    public Flux<String> chat(String sessionId, String messages, String userId, Boolean stream) throws Exception {
        log.info("用户 {} 在会话 {} 中提问：{}", userId, sessionId, messages);

        // 获取或创建会话
        Session session = getOrCreateSession(sessionId, userId, messages);

        // 保存用户消息到数据库
        saveUserMessage(sessionId, userId, messages, session.getTitle());

        if (!stream) {
            // 当前业务逻辑基本只用流式，这里保留一个简单的非流式兜底
            log.info("非流式返回");
            String nonStream = "这是针对[" + messages + "]的返回内容";
        }

        // 转发到 Python Agent 服务的流式返回
        Flux<String> agentFlux = agentProxyService.chatStream(sessionId, userId, messages);

        // 用于累积流式返回的 AI 回复内容
        AtomicReference<StringBuilder> contentBuilder = new AtomicReference<>(new StringBuilder());
        String sessionTitle = session.getTitle();

        return agentFlux
                .doOnNext(data -> {
                    if (data != null && !data.isEmpty()) {
                        String text = extractTextFromSSEChunk(data);
                        if (text != null && !text.isEmpty()) {
                            contentBuilder.get().append(text);
                        }
                    }
                })
                .onErrorResume(error -> { // 处理错误
                    log.error("流式过程中出现错误: {}", error.getMessage());
                    String refined = refineErrorMessage(error).replace("\n", "\\n");
                    String errEvent = String.format(
                            "data: {\"choices\":[{\"index\":0,\"text\":\"%s\",\"finish_reason\":\"stop\",\"model\":\"%s\"}]}\n\n",
                            refined, "gpt-4o-mini"
                    );
                    return Flux.just(errEvent);
                })
                .doOnComplete(() -> {
                    String fullContent = contentBuilder.get().toString();
                    if (!fullContent.isEmpty()) {
                        saveAssistantMessage(sessionId, userId, fullContent, sessionTitle);
                        log.info("AI回复已保存到数据库，会话ID: {}, 内容长度: {}", sessionId, fullContent.length());
                    }
                    log.info("流式对话完成");
                    // 结构化数据的保存由 Python Agent 回调 /ai_assistant/callback 完成
                });
    }

    /**
     * 从 SSE 格式的 chunk 中解析并提取 text 内容
     * 格式: data: {"choices":[{"index":0,"text":"xxx","finish_reason":"stop","model":"..."}]}\n\n
     */
    private String extractTextFromSSEChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        String[] events = chunk.split("\n\n");
        for (String event : events) {
            String trimmed = event.trim();
            if (trimmed.startsWith("data: ")) {
                String json = trimmed.substring(6).trim();
                if (json.isEmpty() || "{}".equals(json)) {
                    continue;
                }
                try {
                    JsonNode root = objectMapper.readTree(json);
                    JsonNode choices = root.get("choices");
                    if (choices != null && choices.isArray() && choices.size() > 0) {
                        JsonNode textNode = choices.get(0).get("text");
                        if (textNode != null && !textNode.isNull()) {
                            result.append(textNode.asText());
                        }
                    }
                } catch (Exception e) {
                    log.debug("解析 SSE JSON 失败，跳过: {}", e.getMessage());
                }
            }
        }
        return result.toString();
    }

    // 获取或创建会话
    private Session getOrCreateSession(String sessionId, String userId, String messages)
            throws InterruptedException, ExecutionException, TimeoutException {
        Session session = sessionMapper.findBySessionId(sessionId);

        if (session == null) {
            // 异步生成会话标题
            CompletableFuture<String> titleFuture = getTitleAsync(messages);
            String title = titleFuture.get(60, SECONDS);
            log.info("生成的标题：{}", title);

            session = new Session();
            session.setSessionId(sessionId);
            session.setUserName("default_user");  // TODO: 后续改成真实用户名
            session.setTitle(title.length() > 10 ? title.substring(0, 10) : title);
            session.setUserId(userId);
            sessionMapper.insert(session);
            log.info("创建新会话：{} 用户：{}", sessionId, userId);
        }
        return session;
    }

    // 保存用户消息
    private void saveUserMessage(String sessionId, String userId, String content, String title) {
        Message userMsg = new Message();
        userMsg.setMsgId(UUID.randomUUID().toString());
        userMsg.setSessionId(sessionId);
        userMsg.setUserName("default_user");
        userMsg.setRole("user");
        userMsg.setTitle(title);
        userMsg.setContent(content);
        chatMessageMapper.insert(userMsg);
        log.debug("保存用户消息：会话 {} 用户 {}", sessionId, userId);
    }

    // 保存 AI 回复
    private void saveAssistantMessage(String sessionId, String userId, String content, String title) {
        Message assistantMsg = new Message();
        assistantMsg.setMsgId(UUID.randomUUID().toString());
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setUserName("assistant");
        assistantMsg.setRole("assistant");
        assistantMsg.setTitle(title);
        assistantMsg.setContent(content);
        chatMessageMapper.insert(assistantMsg);
        log.debug("保存AI回复：会话 {} 用户 {}", sessionId, userId);
    }

    // 获取当前会话历史
    @Override
    public ChatHistoryResponse getHistory(String sessionId) {
        List<Message> messages = chatMessageMapper.findBySessionId(sessionId);
        List<ChatHistoryDTO> result = new ArrayList<>();
        for (Message m : messages) {
            ChatHistoryDTO dto = new ChatHistoryDTO();
            dto.setMsgId(m.getMsgId());
            dto.setRole(m.getRole());
            dto.setContent(m.getContent());
            dto.setModifyTime(m.getModifyTime() != null ? m.getModifyTime().toString() : null);
            result.add(dto);
        }

        ChatHistoryResponse resp = new ChatHistoryResponse();
        resp.setHistoryList(result);
        resp.setTotal(result.size());
        return resp;
    }

    // 获取会话列表
    @Override
    public SessionListResponse getSessionList(Integer page, Integer pageSize, String userId) {
        int offset = (page - 1) * pageSize;
        List<Session> list = sessionMapper.findByUserId(offset, pageSize, userId);
        int total = sessionMapper.count();

        List<SessionDTO> dtoList = new ArrayList<>();
        for (Session s : list) {
            SessionDTO dto = new SessionDTO(
                    s.getSessionId(),
                    s.getModifyTime().toString(),
                    s.getTitle(),
                    s.getDailyRoutes()
            );
            dtoList.add(dto);
        }

        SessionListResponse resp = new SessionListResponse();
        resp.setSessionList(dtoList);
        resp.setPage(page);
        resp.setPageSize(pageSize);
        resp.setTotal(total);
        return resp;
    }

    // 删除会话
    @Override
    public boolean deleteSession(String sessionId) {
        try {
            chatMessageMapper.deleteBySessionId(sessionId);
            int rows = sessionMapper.deleteBySessionId(sessionId);
            return rows > 0;
        } catch (Exception e) {
            log.error("删除会话失败: {}", e.getMessage(), e);
            return false;
        }
    }

    // 修改会话标题
    @Override
    public boolean renameSession(String sessionId, String newTitle) {
        try {
            int rows = sessionMapper.updateTitle(sessionId, newTitle);
            return rows > 0;
        } catch (Exception e) {
            log.error("修改会话标题失败: {}", e.getMessage(), e);
            return false;
        }
    }

    // 用于抛出异常的人性化错误信息
    private String refineErrorMessage(Throwable error) {
        if (error == null) {
            return "服务暂不可用，请稍后重试";
        }
        if (error instanceof InputValidationException) {
            return error.getMessage();
        }
        String msg = String.valueOf(error.getMessage());
        if (msg != null && (msg.contains("免费API限制模型输入token小于4096")
                || msg.contains("prompt tokens")
                || msg.contains("4096")
                || msg.contains("FORBIDDEN"))) {
            return "十分抱歉，免费API对模型输入有 4096 token 上限。";
        }
        return "对话服务暂时出现波动，请稍后再试";
    }

    // 异步生成标题
    private CompletableFuture<String> getTitleAsync(String message) {
        return CompletableFuture.supplyAsync(() -> {
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .build();
            String template = """
                    请根据用户以下的问题生成一个会话标题，注意需要严格限制字数在10个中文字以内！
                    示例输入："请为我规划北京市3日旅游攻略。"
                    示例输出："北京3日旅游攻略"。
                    示例输入："请为我规划广州市3日旅游攻略，我喜欢一些文艺景点。"
                    示例输出："广州3日文艺旅游攻略"。
                    示例输入："请为我规划深圳市3日旅游攻略，我喜欢一些现代景点。"
                    示例输出："深圳3日现代旅游攻略"。
                    用户输入为:{{problem}}
                    """;
            PromptTemplate promptTemplate = PromptTemplate.from(template);
            Map<String, Object> variables = new HashMap<>();
            String trimmed = message;
            if (trimmed != null && trimmed.length() > 4000) {
                trimmed = trimmed.substring(0, 4000);
            }
            variables.put("problem", trimmed);
            Prompt prompt = promptTemplate.apply(variables);
            return stripSurroundingDoubleQuotes(model.chat(prompt.text()));
        });
    }

    // 移除首尾双引号（若存在）
    private String stripSurroundingDoubleQuotes(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * 保存结构化输出数据到数据库
     * 由 Python Agent 的 format_output_node 调用 /ai_assistant/callback 接口触发
     */
    @Override
    public boolean saveStructuredOutput(String sessionId, String structuredOutput) {
        try {
            log.info("保存结构化输出数据，sessionId: {}", sessionId);

            if (sessionId == null || sessionId.trim().isEmpty()) {
                log.warn("sessionId 为空，跳过保存");
                return false;
            }

            if (structuredOutput == null || structuredOutput.trim().isEmpty()) {
                log.warn("structuredOutput 为空，跳过保存");
                return false;
            }

            sessionMapper.updateRoutine(structuredOutput, sessionId);
            log.info("结构化输出数据保存成功，sessionId: {}", sessionId);
            return true;

        } catch (Exception e) {
            log.error("保存结构化输出数据异常: {}", e.getMessage(), e);
            return false;
        }
    }
}


