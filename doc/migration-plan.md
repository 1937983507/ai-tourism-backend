# AI-Tourism Agent 迁移到 Python/LangGraph 方案文档

## 📋 目录

1. [迁移可行性分析](#迁移可行性分析)
2. [架构设计](#架构设计)
3. [功能需求清单](#功能需求清单)
4. [技术实现方案](#技术实现方案)
5. [接口设计](#接口设计)
6. [数据交互方案](#数据交互方案)
7. [迁移步骤](#迁移步骤)
8. [风险评估与应对](#风险评估与应对)

---

## 迁移可行性分析

### ✅ 可行性结论

**完全可行**。将 Agent 部分迁移到 Python/LangGraph 是可行的，原因如下：

1. **职责分离清晰**：
   - Agent 部分主要负责 AI 对话、工具调用、记忆管理
   - Java 部分负责用户认证、数据库操作、REST API、业务逻辑
   - 两者通过 HTTP/gRPC 接口通信，耦合度低

2. **技术栈优势**：
   - Python 生态在 AI/LLM 领域更成熟（LangGraph、LangChain、OpenAI SDK 等）
   - LangGraph 提供更灵活的 Agent 工作流控制
   - 便于集成更多 Python AI 工具和库

3. **现有架构支持**：
   - 当前系统已采用分层架构，Agent 层相对独立
   - 通过接口抽象，可以无缝替换实现

### ⚠️ 注意事项

1. **架构解耦**：采用单向调用和工具化交互，避免相互依赖
2. **记忆管理**：使用 LangGraph Checkpoint 机制，不再依赖 Redis
3. **验证机制**：使用 LangGraph 条件节点实现，不继承 langchain4j 的护轨
4. **性能考虑**：Python 服务独立部署，通过工具调用减少耦合
5. **部署复杂度**：需要同时维护 Java 和 Python 两个服务，但职责清晰

---

## 架构设计

### 整体架构图（解耦设计）

```
┌─────────────────────────────────────────────────────────────┐
│                        前端 (Vue)                            │
└───────────────────────┬───────────────────────────────────────┘
                        │ HTTP/SSE
┌───────────────────────┴───────────────────────────────────────┐
│                    Java API Gateway                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │  Controller  │  │   Service    │  │   Mapper     │         │
│  │  (REST API)  │  │  (业务逻辑)  │  │  (数据库)    │         │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘         │
│         │                 │                  │                │
│         └─────────────────┴──────────────────┘                │
│                        │                                        │
│                        │ HTTP Request (单向)                    │
└────────────────────────┼────────────────────────────────────────┘
                         │
┌────────────────────────┴────────────────────────────────────────┐
│              Python Agent 服务 (LangGraph)                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │  LangGraph   │  │   Tools      │  │  Checkpoint  │         │
│  │  工作流引擎   │  │  (Function   │  │  (内置持久化)│         │
│  │              │  │   Call/MCP)  │  │              │         │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘         │
│         │                 │                  │                │
│         └─────────────────┴──────────────────┘                │
│                        │                                        │
│                        │ HTTP Tool Call (单向)                  │
│                        ▼                                        │
│              ┌──────────────────┐                              │
│              │  Java Tool API   │  ← Agent 通过工具调用 Java   │
│              │  (只读业务接口)   │    而不是直接依赖 Java      │
│              └──────────────────┘                              │
└─────────────────────────────────────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
┌───────▼──────┐  ┌───────▼──────┐  ┌───────▼──────┐
│   MySQL      │  │  PostgreSQL  │  │   OpenAI API  │
│  (业务数据)   │  │  (Checkpoint) │  │   (LLM)      │
└──────────────┘  └──────────────┘  └──────────────┘
```

### 架构设计原则

#### ✅ 核心原则：完全解耦

1. **单向依赖**：Java → Python（单向调用），Python Agent 不直接依赖 Java 服务
2. **工具化交互**：Python Agent 通过工具（Tool）调用 Java 业务接口，而非服务间直接耦合
3. **独立演进**：两个服务可以独立开发、部署、扩展，互不影响

### 服务拆分说明

#### Java 服务职责（保留）
- ✅ **API Gateway**：用户认证与权限管理（Sa-Token）
- ✅ **REST API**：对外提供业务接口（Controller 层）
- ✅ **业务逻辑**：数据处理、业务规则（Service 层）
- ✅ **数据持久化**：数据库操作（MyBatis Mapper）
- ✅ **会话管理**：会话的 CRUD 操作
- ✅ **消息持久化**：保存用户消息和 AI 回复到数据库
- ✅ **工具接口**：为 Agent 提供只读业务数据接口（如 POI 查询）
- ✅ **监控**：指标收集（Prometheus）

#### Python Agent 服务职责（迁移）
- ✅ **AI 对话处理**：LangGraph 工作流编排
- ✅ **工具调用管理**：Function Call + MCP 工具
- ✅ **状态管理**：使用 LangGraph Checkpoint 机制（替代 Redis）
- ✅ **输入验证**：使用 LangGraph 条件节点实现（替代护轨）
- ✅ **流式响应**：SSE 流式返回
- ✅ **结构化输出**：JSON Schema 输出
- ✅ **独立运行**：不依赖 Java 服务，仅通过工具接口交互

---

## 功能需求清单

### 1. 核心 Agent 功能

#### 1.1 对话处理能力
- [x] **流式对话**：支持 SSE 流式返回 AI 响应
- [x] **会话隔离**：每个 sessionId 独立的对话上下文
- [x] **多轮对话**：支持基于历史消息的连续对话
- [x] **系统提示词**：支持从文件加载系统提示词模板
- [x] **结构化输出**：支持 JSON Schema 输出结构化路线数据

#### 1.2 工具调用能力
- [x] **Function Call 工具**：
  - `weatherForecast(cityName, dayCount)` - 天气预报工具
  - `poiSearch(cityName, poiCount)` - 景点搜索工具
- [x] **MCP 工具支持**：
  - 支持通过 SSE 连接 MCP 服务器
  - 支持多个 MCP 客户端配置
- [x] **工具管理器**：统一注册和管理所有工具
- [x] **工具缓存**：使用缓存避免重复调用外部 API

#### 1.3 记忆管理能力（LangGraph Checkpoint 机制）
- [x] **Checkpoint 持久化**：
  - 使用 LangGraph 内置 Checkpoint 机制（MemorySaver/PostgresSaver）
  - 支持状态持久化和恢复
  - 支持"时间旅行"和状态回溯
  - **不再依赖 Redis**（除非多实例分布式部署）
- [x] **消息状态管理**：
  - 使用 MessagesState 管理对话历史
  - 支持消息窗口限制（默认 20 条）
  - 自动消息修剪（保留最近 N 条）
- [x] **会话恢复**：
  - 通过 thread_id 恢复会话状态
  - 支持中断后继续对话
- [x] **长期记忆**（可选，暂时不需要）：
  - 使用向量数据库存储跨会话记忆
  - 语义检索相关历史信息

#### 1.4 安全与验证（LangGraph 原生实现）
- [x] **输入验证节点**：
  - 使用条件节点（Conditional Node）实现输入校验
  - 敏感词检测
  - Prompt 注入攻击检测
  - 输入长度校验（最大 1000 字符）
  - 空内容检测
- [x] **状态验证**：
  - 使用 TypedDict 强类型约束
  - 自定义 Reducer 实现状态更新验证
- [x] **错误处理节点**：
  - 工具调用失败时的降级策略
  - 自动重试机制
  - 错误信息格式化

### 2. LangGraph 工作流功能

#### 2.1 节点定义
- [x] **意图理解节点**：解析用户需求（城市、天数、偏好等）
- [x] **天气查询节点**：调用天气工具获取预报信息
- [x] **景点查询节点**：调用 POI 搜索工具获取景点信息
- [x] **路线规划节点**：基于天气和景点信息生成旅游攻略
- [x] **结构化输出节点**：将攻略转换为 JSON Schema 格式

#### 2.2 流程控制
- [x] **条件路由**：根据用户输入决定执行路径
- [x] **并行执行**：天气和景点查询可以并行执行
- [x] **错误处理**：工具调用失败时的降级策略
- [x] **重试机制**：支持失败重试

#### 2.3 状态管理
- [x] **状态持久化**：工作流状态可持久化到 Redis
- [x] **状态恢复**：支持从持久化状态恢复工作流
- [x] **状态清理**：工作流完成后自动清理状态

### 3. 接口与通信

#### 3.1 HTTP 接口（Java 调用 Python）
- [x] **流式对话接口**：
  - `POST /agent/chat-stream`
  - 请求参数：`sessionId`, `userId`, `message`
  - 返回：SSE 流式响应
- [x] **健康检查接口**：
  - `GET /agent/health`
  - 返回服务健康状态
- [x] **工具列表接口**（可选）：
  - `GET /agent/tools`
  - 返回可用工具列表

#### 3.2 数据共享（解耦设计）
- [x] **Checkpoint 存储**（Python Agent 独立管理）：
  - 暂时使用内存进行存储，后续再转换到以下方案
  - 使用 PostgreSQL 或 SQLite 存储 Checkpoint
  - Key 格式：`thread_id`（由 sessionId 映射）
  - 自动管理状态持久化
- [x] **业务数据共享**（通过工具接口）：
  - Java 提供只读业务接口（如 POI 查询）
  - Python Agent 通过工具调用获取数据
  - 不直接访问 MySQL
- [x] **会话元数据**（Java 管理）：
  - 会话表：`t_ai_assistant_sessions`（Java 管理）
  - 消息表：`t_ai_assistant_chat_messages`（Java 保存）
  - Python Agent 通过 thread_id 关联，不直接操作数据库

### 4. 监控与可观测性

#### 4.1 指标收集
- [x] **Agent 指标**：
  - 请求总数
  - 响应时间
  - Token 消耗
  - 工具调用次数
  - 缓存命中率
  - 错误率
- [x] **工具指标**：
  - 工具调用耗时
  - 工具调用成功率
  - 缓存命中率

#### 4.2 日志记录
- [x] **结构化日志**：使用 JSON 格式记录日志
- [x] **日志级别**：支持 DEBUG、INFO、WARN、ERROR
- [x] **追踪 ID**：支持分布式追踪

---

## 技术实现方案

### Python 技术栈

#### 核心框架
```python
# LangGraph - Agent 工作流引擎
langgraph >= 0.2.0

# LangChain - LLM 集成
langchain >= 0.3.0
langchain-openai >= 0.2.0

# FastAPI - Web 框架
fastapi >= 0.115.0
uvicorn >= 0.32.0
sse-starlette >= 2.1.0  # SSE 支持

# Checkpoint 持久化（替代 Redis）
langgraph-checkpoint-postgres >= 0.1.0  # PostgreSQL Checkpoint
# 或
langgraph-checkpoint-sqlite >= 0.1.0    # SQLite Checkpoint（开发环境）

# 向量数据库（长期记忆，可选）
langchain-pinecone >= 0.1.0  # 或 langchain-milvus, langchain-weaviate

# HTTP 客户端（工具调用 Java 接口）
httpx >= 0.27.0

# 工具库
pydantic >= 2.9.0  # 数据验证
httpx >= 0.27.0  # HTTP 客户端
```

#### 项目结构
```
ai-tourism-agent/
├── app/
│   ├── __init__.py
│   ├── main.py                 # FastAPI 应用入口
│   ├── config.py               # 配置管理
│   │
│   ├── agent/                  # Agent 核心模块
│   │   ├── __init__.py
│   │   ├── graph.py            # LangGraph 工作流定义
│   │   ├── nodes.py            # 工作流节点实现
│   │   ├── state.py            # 状态定义
│   │   └── service.py          # Agent 服务封装
│   │
│   ├── tools/                  # 工具模块
│   │   ├── __init__.py
│   │   ├── base.py             # 工具基类
│   │   ├── weather.py          # 天气预报工具
│   │   ├── poi.py              # 景点搜索工具
│   │   ├── manager.py          # 工具管理器
│   │   └── mcp/                # MCP 工具支持
│   │       ├── __init__.py
│   │       ├── client.py      # MCP 客户端
│   │       └── provider.py     # MCP 工具提供者
│   │
│   ├── checkpoint/              # Checkpoint 管理模块
│   │   ├── __init__.py
│   │   ├── config.py           # Checkpoint 配置
│   │   └── saver.py             # Checkpoint Saver 初始化
│   │
│   ├── utils/                  # 工具函数
│   │   ├── __init__.py
│   │   ├── cache.py            # 缓存工具
│   │   └── logger.py           # 日志工具
│   │
│   └── api/                    # API 路由
│       ├── __init__.py
│       ├── routes.py           # 路由定义
│       └── models.py           # 请求/响应模型
│
├── prompts/                    # Prompt 模板
│   └── tour-route-planning-system-prompt.txt
│
├── requirements.txt            # Python 依赖
├── .env                        # 环境变量配置
└── README.md                   # 项目说明
```

### LangGraph 工作流设计

#### 状态定义（使用 MessagesState）
```python
from typing import TypedDict, List, Optional, Annotated
from langchain_core.messages import BaseMessage
from langgraph.graph import MessagesState
from operator import add

class AgentState(MessagesState):
    """Agent 工作流状态（继承 MessagesState）"""
    session_id: str
    user_id: str
    weather_data: Optional[str] = None
    poi_data: Optional[str] = None
    route_plan: Optional[str] = None
    structured_output: Optional[dict] = None
    error: Optional[str] = None
    # messages 字段由 MessagesState 自动管理
```

#### 工作流图（包含验证节点）
```python
from langgraph.graph import StateGraph, END
from langgraph.checkpoint.postgres import PostgresSaver

def create_agent_graph(checkpointer: PostgresSaver):
    """创建 Agent 工作流图"""
    graph = StateGraph(AgentState)
    
    # 1. 输入验证节点（替代护轨）
    graph.add_node("validate_input", validate_input_node)
    
    # 2. 意图理解节点
    graph.add_node("intent_understanding", intent_understanding_node)
    
    # 3. 工具调用节点（并行）
    graph.add_node("fetch_weather", fetch_weather_node)
    graph.add_node("fetch_poi", fetch_poi_node)
    
    # 4. 路线规划节点
    graph.add_node("plan_route", plan_route_node)
    
    # 5. 格式化输出节点
    graph.add_node("format_output", format_output_node)
    
    # 6. 错误处理节点
    graph.add_node("handle_error", handle_error_node)
    
    # 设置入口
    graph.set_entry_point("validate_input")
    
    # 添加边和条件路由
    graph.add_edge("validate_input", "intent_understanding")
    graph.add_edge("intent_understanding", "fetch_weather")
    graph.add_edge("intent_understanding", "fetch_poi")
    
    # 条件路由：根据工具调用结果决定下一步
    graph.add_conditional_edges(
        "fetch_weather",
        check_tool_result,
        {
            "success": "plan_route",
            "error": "handle_error"
        }
    )
    graph.add_edge("fetch_poi", "plan_route")
    graph.add_edge("plan_route", "format_output")
    graph.add_edge("format_output", END)
    graph.add_edge("handle_error", END)
    
    # 编译时配置 Checkpoint
    return graph.compile(checkpointer=checkpointer)

# 输入验证节点实现（替代护轨）
def validate_input_node(state: AgentState) -> AgentState:
    """输入验证节点"""
    last_message = state["messages"][-1].content
    
    # 敏感词检测
    if contains_sensitive_words(last_message):
        state["error"] = "输入包含不当内容"
        return state
    
    # 长度检查
    if len(last_message) > 1000:
        state["error"] = "输入内容过长"
        return state
    
    # 空内容检查
    if not last_message.strip():
        state["error"] = "输入内容不能为空"
        return state
    
    return state
```

### 工具实现示例

#### 天气预报工具
```python
from langchain.tools import tool
from functools import lru_cache
import httpx

@tool
def weather_forecast(city_name: str, day_count: int = 7) -> str:
    """
    根据城市名获取未来若干天的逐天天气预报，天数范围1-16
    
    Args:
        city_name: 城市名称，例如: 北京 / Shanghai / New York
        day_count: 要返回的预测天数，范围1-16
    
    Returns:
        天气预报 JSON 字符串
    """
    # 实现逻辑（与 Java 版本保持一致）
    # 1. 地理编码获取经纬度
    # 2. 调用 Open-Meteo API
    # 3. 格式化返回结果
    pass
```

#### 景点搜索工具（通过工具调用 Java）
```python
@tool
def poi_search(city_name: str, poi_count: int = 10) -> str:
    """
    根据城市名获取景点信息
    
    Args:
        city_name: 城市名称，例如: 北京、上海、西安（不要加后缀）
        poi_count: 要返回的景点数量，例如: 10
    
    Returns:
        景点信息 JSON 字符串
    """
    # 通过 HTTP 调用 Java 服务提供的工具接口
    # 注意：这是工具调用，不是服务依赖
    async with httpx.AsyncClient() as client:
        response = await client.get(
            f"{JAVA_SERVICE_URL}/api/tools/poi",
            params={"city_name": city_name, "count": poi_count},
            timeout=10.0
        )
        return response.json()
```

### 记忆管理实现（LangGraph Checkpoint）

#### Checkpoint 配置（替代 Redis）
```python
from langgraph.checkpoint.postgres import PostgresSaver
from langgraph.checkpoint.memory import MemorySaver
import os

def create_checkpointer():
    """创建 Checkpoint Saver"""
    # 生产环境：使用 PostgreSQL
    if os.getenv("ENV") == "production":
        conn_string = os.getenv("POSTGRES_CONN_STRING")
        return PostgresSaver.from_conn_string(conn_string)
    
    # 开发环境：使用内存（或 SQLite）
    return MemorySaver()

# 使用示例
checkpointer = create_checkpointer()
graph = create_agent_graph(checkpointer)

# 通过 thread_id 恢复会话
config = {
    "configurable": {
        "thread_id": "session_123"  # 对应 Java 的 sessionId
    }
}

# 调用时会自动保存和恢复状态
result = graph.invoke(
    {"messages": [HumanMessage(content="用户消息")]},
    config=config
)
```

#### 消息历史管理（自动处理）
```python
# LangGraph 自动管理 messages，无需手动操作
# 通过 MessagesState 和 Checkpoint 机制自动：
# 1. 保存对话历史
# 2. 恢复历史状态
# 3. 修剪消息窗口（保留最近 N 条）

# 如果需要从 Java 加载历史消息（首次对话）
async def load_history_from_java(session_id: str) -> List[BaseMessage]:
    """从 Java 服务加载历史消息（仅首次）"""
    async with httpx.AsyncClient() as client:
        response = await client.get(
            f"{JAVA_SERVICE_URL}/ai_assistant/get_history",
            params={"session_id": session_id},
            headers={"Authorization": f"Bearer {INTERNAL_TOKEN}"}
        )
        # 转换为 LangChain Messages
        return convert_to_messages(response.json())
```

---

## 接口设计

### Java 调用 Python Agent 接口（单向调用）

#### 1. 流式对话接口

**请求**
```http
POST /agent/chat-stream
Content-Type: application/json
Authorization: Bearer <token>

{
  "session_id": "session_123",
  "user_id": "user_456",
  "message": "请为我规划北京市3日旅游攻略"
}
```

**响应**（SSE 流式）
```
data: {"token": "请", "finish_reason": null}

data: {"token": "稍", "finish_reason": null}

...

data: {"token": "。", "finish_reason": "stop"}
```

#### 2. 健康检查接口

**请求**
```http
GET /agent/health
```

**响应**
```json
{
  "status": "healthy",
  "version": "1.0.0",
  "checkpoint_backend": "postgres",
  "tools": {
    "weatherForecast": "available",
    "poiSearch": "available",
    "mcp": "available"
  }
}
```

### Python Agent 调用 Java 服务（通过工具接口）

#### 重要原则：Python Agent 不直接调用 Java 服务，而是通过工具（Tool）调用

#### Java 需要提供的工具接口（只读业务数据）

**1. POI 查询接口**
```http
GET /api/tools/poi?city_name=北京&count=10
Authorization: Bearer <internal_token>
```

**2. 用户信息查询接口（如需要）**
```http
GET /api/tools/user/{user_id}
Authorization: Bearer <internal_token>
```

**注意**：
- 这些接口是**工具接口**，不是服务依赖
- Python Agent 通过工具定义调用，而不是直接依赖 Java 服务
- 工具调用失败时，Agent 可以降级处理，不影响整体流程

---

## 数据交互方案（解耦设计）

### 1. Checkpoint 存储（Python Agent 独立管理）

#### 状态持久化
- **存储方式**：PostgreSQL 或 SQLite（开发环境）
- **Key 格式**：`thread_id`（由 sessionId 映射）
- **自动管理**：LangGraph Checkpoint 机制自动处理
- **优势**：支持状态恢复、时间旅行、多实例共享（PostgreSQL）

#### 不再使用 Redis
- ❌ **不再需要 Redis** 存储对话记忆
- ✅ 使用 LangGraph 内置 Checkpoint 机制
- ✅ 仅在多实例分布式部署时，使用 PostgreSQL 作为 Checkpoint 后端

### 2. 业务数据交互（通过工具接口）

#### 会话元数据（Java 管理）
- **会话表**：`t_ai_assistant_sessions`（Java 负责 CRUD）
- **消息表**：`t_ai_assistant_chat_messages`（Java 负责保存）
- **关联方式**：Python Agent 通过 `thread_id` 关联 `sessionId`

#### 业务数据查询（工具调用）
- **POI 数据**：Python Agent 通过 `poi_search` 工具调用 Java 接口
- **用户数据**：如需要，通过工具接口获取
- **原则**：Python Agent 不直接访问 MySQL，只通过工具接口

### 3. 数据同步策略（解耦）

1. **对话状态**：
   - Python Agent 使用 Checkpoint 独立管理
   - Java 服务不直接访问 Checkpoint
   - 通过 `thread_id` 关联 `sessionId`

2. **消息持久化**：
   - Java 服务监听 SSE 流，自动保存消息到数据库
   - Python Agent 不负责消息持久化，只负责生成

3. **会话管理**：
   - Java 服务负责会话的 CRUD
   - Python Agent 通过 `thread_id` 使用会话，不直接操作数据库

4. **业务数据**：
   - Java 提供只读工具接口
   - Python Agent 通过工具调用获取数据
   - 工具调用失败时，Agent 可以降级处理

---

## 总结

### 迁移优势

1. ✅ **更灵活的 Agent 控制**：LangGraph 提供强大的工作流控制能力
2. ✅ **更丰富的 AI 生态**：Python 在 AI 领域有更成熟的工具和库
3. ✅ **更好的可扩展性**：易于添加新的工具和工作流节点
4. ✅ **职责分离**：Java 专注业务逻辑，Python 专注 AI 能力

### 迁移挑战

1. ⚠️ **性能开销**：增加网络调用，可能影响响应时间
2. ⚠️ **部署复杂度**：需要同时维护两个服务
3. ⚠️ **数据一致性**：需要确保两个服务之间的数据同步

### 建议

1. **分阶段迁移**：先迁移核心功能，逐步完善
2. **保留备份**：保留 Java 版本作为降级方案
3. **充分测试**：在测试环境充分验证后再上线
4. **持续监控**：上线后持续监控性能和稳定性

---

## 附录

### A. 参考资源

- [LangGraph 官方文档](https://langchain-ai.github.io/langgraph/)
- [LangChain 官方文档](https://python.langchain.com/)
- [FastAPI 官方文档](https://fastapi.tiangolo.com/)

### B. 关键配置示例

#### Python Agent 配置（.env）
```env
# OpenAI 配置
OPENAI_API_KEY=sk-xxx
OPENAI_BASE_URL=https://api.chatanywhere.org
OPENAI_MODEL_NAME=gpt-4o-mini
OPENAI_MAX_OUTPUT_TOKENS=800

# Checkpoint 配置（替代 Redis）
CHECKPOINT_TYPE=postgres  # 或 sqlite（开发环境）
POSTGRES_CONN_STRING=postgresql://user:password@localhost/langgraph_checkpoint
# 或 SQLite
# SQLITE_DB_PATH=./checkpoints.db

# Java 服务工具接口（Agent 通过工具调用）
JAVA_SERVICE_URL=http://localhost:8290
JAVA_SERVICE_INTERNAL_TOKEN=xxx

# MCP 配置
MCP_AMAP_URL=https://mcp.amap.com/sse?key=xxx
MCP_RESULT_TRUNCATION_ENABLED=true
MCP_RESULT_TRUNCATION_MAX_LENGTH=2000

# 服务配置
AGENT_PORT=8291
AGENT_HOST=0.0.0.0
LOG_LEVEL=INFO
```

### C. 关键代码示例

#### FastAPI 应用入口
```python
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.agent.service import AgentService
from app.api.routes import router

app = FastAPI(title="AI Tourism Agent Service")

# CORS 配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由
app.include_router(router, prefix="/agent")

# 初始化 Agent 服务
agent_service = AgentService()

@app.on_event("startup")
async def startup():
    await agent_service.initialize()

@app.on_event("shutdown")
async def shutdown():
    await agent_service.cleanup()
```
