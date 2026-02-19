package com.example.aitourism.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 请求：Agent回调接口
 * 用于接收Python Agent格式化后的结构化JSON数据
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatCallbackRequest {
    private String sessionId;
    private String userId;
    private String structuredOutput; // JSON字符串格式的结构化路线数据
}

