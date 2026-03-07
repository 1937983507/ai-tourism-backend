# AI 智能旅游规划助手（后端服务）

> **访问地址**：[https://www.aitrip.chat/](https://www.aitrip.chat/)  
> **欢迎体验智能旅游规划服务！**

## 📖 项目简介

**AI-Tourism Backend** 是智能旅游规划系统的**后端 API 服务**，基于 **Spring Boot、MySQL、MyBatis、Sa-Token** 等技术栈构建。

该服务作为前端与 Python Agent 服务之间的**应用服务层**，主要负责**API 网关、业务逻辑处理、数据持久化、流式响应处理**等核心功能。所有 AI Agent 相关的功能（如 LangGraph 工作流、工具调用、AI 对话处理等）已剥离到独立的 Python Agent 服务中。

### 🎯 核心特性
- **API 网关与请求路由** - 作为 API 网关，将前端请求路由到 Python Agent 服务，处理 SSE 流式响应
- **会话与消息管理** - 提供完整的会话生命周期管理和消息持久化能力，支持历史记录查询
- **用户认证与权限管理** - 基于 Sa-Token 的 JWT 认证体系，实现细粒度的权限控制
- **业务数据接口** - 为 Agent 服务提供业务数据查询接口（如 POI 查询），支持数据服务化
- **微服务架构** - 遵循微服务架构原则，职责清晰，与 Agent 服务完全解耦

---

### 🖼️ AI 智能旅游规划 前端效果截图
![前端效果图](assets/界面图.png)

### 📹 视频效果
![演示视频](./assets/demo.gif)

**AI 智能旅游规划系统**采用前后端分离架构。用户在前端输入自然语言后，请求经过后端 API 服务转发到 **Python Agent 服务**，由 Agent 服务调用工具获取天气、景点等信息，生成旅游路线规划。后端 API 服务负责处理流式返回、会话管理和数据持久化。

---

## 💡 核心特性与架构特点

### 1. 应用服务架构设计
- **职责清晰**：专注于业务逻辑处理、API 网关、数据持久化，不涉及 AI 能力
- **服务解耦**：与 Python Agent 服务完全解耦，通过 HTTP 接口通信，遵循微服务架构原则
- **流式处理**：使用 `Reactor` 处理 SSE 流式响应，实时转发给前端，支持高并发场景

### 2. API 网关与流式响应
- **请求代理**：通过 `AgentProxyService` 实现 API 网关功能，将前端请求路由到 Python Agent 服务
- **SSE 流式处理**：接收 Agent 服务的 SSE 流式响应，通过响应式编程实时转发给前端
- **容错机制**：完善的错误处理、超时控制和降级策略，保障服务高可用性

### 3. 会话与消息管理
- **会话管理**：会话的创建、查询、删除、重命名等操作
- **消息持久化**：用户消息和 AI 回复保存到数据库，支持历史记录查询
- **标题生成**：使用 LLM 自动生成会话标题，提升用户体验

### 4. 用户认证与权限管理
- **Sa-Token 认证**：基于 `JWT` 的短期令牌 + `Refresh Token` 长期令牌机制
- **权限控制**：注解式权限控制（`@SaCheckLogin`、`@SaCheckPermission`），细粒度角色管理
- **用户管理**：用户注册、登录、权限分配等功能

### 5. 工具接口提供
- **POI 查询接口**：为 Python Agent 服务提供景点数据查询接口
- **业务数据接口**：提供只读的业务数据接口，供 Agent 服务调用

### 6. SpringBoot 工程化与 RESTful 设计
- **分层架构**：标准的分层架构（`Controller` - `Service` - `Mapper`）
- **接口规范**：接口统一，符合 `RESTful` 规范，易于前后端协作

---

## 🏗️ 系统整体架构

```
┌─────────────────┐
│   前端 (Vue)     │
│  ai-tourism-     │
│  frontend        │
└────────┬─────────┘
         │ HTTP/SSE
         │
┌────────▼─────────────────────────────────────┐
│   后端 API 服务 (Spring Boot)                 │
│   ai-tourism-backend                          │
│                                               │
│  ┌─────────────────────────────────────┐   │
│  │ Controller 层                         │   │
│  │ - ChatController (对话接口)           │   │
│  │ - AuthController (认证接口)            │   │
│  │ - ToolController (工具接口)            │   │
│  └──────────────┬────────────────────────┘   │
│                 │                             │
│  ┌──────────────▼────────────────────────┐   │
│  │ Service 层                             │   │
│  │ - AssistantChatService (会话管理)      │   │
│  │ - AgentProxyService (请求转发)         │   │
│  │ - AuthService (用户认证)               │   │
│  │ - PoiToolService (POI查询)            │   │
│  └──────────────┬────────────────────────┘   │
│                 │                             │
│  ┌──────────────▼────────────────────────┐   │
│  │ Mapper 层 (MyBatis)                   │   │
│  │ - SessionMapper                       │   │
│  │ - ChatMessageMapper                   │   │
│  │ - UserMapper                          │   │
│  └──────────────┬────────────────────────┘   │
└─────────────────┼─────────────────────────────┘
                  │
         ┌────────▼────────┐
         │   MySQL 数据库   │
         │ 会话、消息、用户 │
         └─────────────────┘
                  │
         ┌────────▼────────┐
         │ HTTP/SSE        │
         │                 │
┌────────▼─────────────────▼────────┐
│   Python Agent 服务               │
│   ai-tourism-agent                │
│   - LangGraph 工作流              │
│   - AI 对话处理                   │
│   - 工具调用 (MCP/Function Call)  │
│   - 结构化输出                    │
└──────────────────────────────────┘
```

### 架构说明

- **前端（ai-tourism-frontend）**：`Vue` 应用，负责交互、地图渲染与对话展示；通过 `SSE` 调用 `POST /ai_assistant/chat-stream` 实时消费模型输出

- **后端 API 服务（ai-tourism-backend）**：
  - **接入层（Controller + 鉴权）**：基于 `Spring Boot REST`，使用 `Sa-Token` 进行登录与权限校验，提供 RESTful API 接口
  - **业务服务层**：
    - `AssistantChatService`：统一处理会话管理、消息入库、流式返回转发
    - `AgentProxyService`：实现 API 网关功能，将请求路由到 Python Agent 服务，处理 SSE 流式响应
    - `AuthService`：用户认证与权限管理
    - `PoiToolService`：为 Agent 服务提供 POI 查询接口
  - **数据访问层（MyBatis）**：通过 `MyBatis` 实现数据持久化，管理会话表、消息表、用户表等

- **Python Agent 服务（ai-tourism-agent）**：
  - **AI 对话处理**：LangGraph 工作流编排
  - **工具调用管理**：Function Call + MCP 工具
  - **状态管理**：使用 LangGraph Checkpoint 机制
  - **流式响应**：SSE 流式返回
  - **结构化输出**：JSON Schema 输出



---

## 🚀 快速开始

### 📂 目录结构

```
ai-tourism-backend/
├── src/
│   ├── main/
│   │   ├── java/com/example/aitourism/
│   │   │   ├── config/              # 配置类（如Sa-Token、CORS等）
│   │   │   ├── controller/          # REST API 控制器
│   │   │   │   ├── ChatController.java      # 对话接口
│   │   │   │   ├── AuthController.java      # 认证接口
│   │   │   │   └── ToolController.java      # 工具接口
│   │   │   ├── dto/                 # 数据传输对象
│   │   │   ├── entity/              # 实体类
│   │   │   ├── exception/           # 全局异常处理
│   │   │   ├── mapper/              # MyBatis 映射
│   │   │   ├── service/             # 业务逻辑层
│   │   │   │   ├── AgentProxyService.java   # Agent 代理服务（请求转发）
│   │   │   │   ├── AssistantChatService.java # 会话管理服务
│   │   │   │   ├── AuthService.java         # 用户认证服务
│   │   │   │   └── PoiToolService.java      # POI 查询服务
│   │   │   └── util/                # 工具类
│   │   └── resources/
│   │       ├── application.yml      # 主要配置文件
│   │       └── mapper/              # MyBatis XML 映射文件
├── sql/
│   └── create_table.sql             # 数据库表结构
├── doc/
│   ├── API.md                       # 接口文档
│   └── Prometheus-Grafana.json      # 监控仪表盘配置
├── pom.xml                          # Maven 依赖
└── README.md
```

### 🛠️ 技术栈与依赖

| 技术分类 | 技术栈 | 版本/说明 |
|---------|--------|----------|
| **核心框架** | Java | `21` |
| | Spring Boot | `3.5.6` |
| **数据库** | MySQL | `9.4` |
| **ORM** | MyBatis & MyBatis-Spring-Boot | 数据持久化 |
| **安全认证** | Sa-Token | JWT 认证与权限 |
| | BCrypt | 密码加密 |
| **响应式编程** | Spring WebFlux | 流式响应处理 |
| | Reactor | 响应式流处理 |
| **工具库** | Lombok | 代码简化 |
| | Hutool | 工具库 |
| **监控** | Prometheus + Grafana | 监控与可视化 |
| | Micrometer | Spring Boot 监控埋点 |

> 详见 [pom.xml](pom.xml) 依赖配置 

### 🗄️ 数据库结构

#### 主要表设计

| 表名 | 说明 | 主要字段 |
|------|------|----------|
| `t_user` | 用户表 | 手机号、加密密码、昵称、头像、状态等 |
| `t_role` | 角色表 | USER、ROOT 等角色 |
| `t_permission` | 权限表 | 权限标识、权限名称等 |
| `t_user_role` | 用户-角色关联表 | 用户ID、角色ID |
| `t_role_permission` | 角色-权限关联表 | 角色ID、权限ID |
| `t_refresh_token` | 刷新令牌表 | 用户ID、令牌值、过期时间等 |
| `t_ai_assistant_sessions` | 会话列表 | 会话ID、用户ID、会话标题等 |
| `t_ai_assistant_chat_messages` | AI助手消息表 | 消息ID、会话ID、消息内容、角色等 |
| `t_poi` | 景点POI数据 | 景点名、所属城市、景点描述 |

> 详细字段和约束请参考 [sql/create_table.sql](sql/create_table.sql)

### ⚙️ 配置说明

主要配置项在 `src/main/resources/application.yml`：

- **基础配置**：端口、数据库连接、日志、MyBatis 等
- **安全认证**：Sa-Token JWT 密钥、token 过期时间、权限注解等  
- **Agent 服务配置**：Python Agent 服务地址、内部 Token 等
- **OpenAI 配置**：用于生成会话标题的 LLM 配置

### 🔗 接口说明

#### 用户与认证相关

| 接口 | 方法 | 说明 |
|------|------|------|
| `/auth/login` | `POST` | 用户登录，返回 token、用户信息等 |
| `/auth/register` | `POST` | 用户注册，自动分配 USER 角色 |
| `/auth/me` | `GET` | 获取当前用户信息及角色 |
| `/auth/refresh` | `POST` | 刷新 token，提升安全性与体验 |
| `/auth/logout` | `POST` | 登出，清理会话 |
| `/auth/disable` | `POST` | 禁用用户（需权限） |
| `/auth/set_root` | `POST` | ROOT 授权（需权限） |

#### AI 助手相关

| 接口 | 方法 | 说明 |
|------|------|------|
| `/ai_assistant/chat-stream` | `POST` | 发起 AI 流式对话，转发到 Python Agent 服务并返回 SSE 流式响应 |
| `/ai_assistant/get_history` | `POST` | 获取会话历史，支持多轮追溯 |
| `/ai_assistant/session_list` | `POST` | 获取历史会话列表，分页展示 |
| `/ai_assistant/delete_session` | `POST` | 删除会话 |
| `/ai_assistant/rename_session` | `POST` | 重命名会话 |
| `/ai_assistant/callback` | `POST` | Agent 服务回调接口，用于保存结构化输出数据 |

#### 工具接口（供 Agent 服务调用）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/tool/poi` | `GET` | 查询景点 POI 数据（供 Agent 服务调用） |

> 详细参数与返回格式请参考 [doc/API.md](doc/API.md)

---



## 🛫 部署与运行

### 📋 环境要求
1. **JDK 21** - Java 运行环境
2. **Maven** - 项目构建工具
3. **MySQL 9.4** - 数据库
4. **Python Agent 服务** - 需要启动独立的 Python Agent 服务（参考 [ai-tourism-agent 仓库](https://github.com/19337983507/ai-tourism-agent)）

### 🚀 部署步骤

#### 1️. 环境准备
```bash
# 安装 JDK 21
# 安装 MySQL 9.4
```

#### 2️. 数据库初始化
```bash
# 执行数据库初始化脚本
mysql -u root -p < sql/create_table.sql
```

#### 3️. 启动 Python Agent 服务
确保 Python Agent 服务已启动并运行在配置的端口（默认 `8291`）

#### 4️. 配置文件
编辑 `src/main/resources/application.yml`：
- 配置数据库连接信息
- 配置 Python Agent 服务地址（`agent.base-url`）
- 配置 OpenAI API Key（用于生成会话标题）
- 配置其他必要参数

#### 5️. 构建运行
```bash
# 构建项目
mvn clean package

# 运行项目
java -jar target/ai-tourism-0.0.1-SNAPSHOT.jar
```

#### 6️. 前端部署
前端请参考 [ai-tourism-frontend 仓库](https://github.com/1937983507/ai-tourism-frontend)

---

## 📬 联系与贡献

欢迎任何建议、反馈与贡献！如需交流或有合作意向，欢迎通过以下方式联系：

- **微信**：`13859211947`
- **GitHub**：提交 Issue 或 PR 到本仓库
- **前端项目**：[ai-tourism-frontend 仓库](https://github.com/1937983507/ai-tourism-frontend)

如有 Bug、需求或想法，欢迎随时提出，我们会积极响应。
也欢迎 Java + AI 应用开发相关的同学一起交流讨论。

---

## 📝 License

本项目仅供学习使用，**禁止未经授权的商用**。

---

## 📋 TODO list

### 1. 后端 API 服务优化
- [ ] 优化流式响应处理，提升 API 网关转发性能
- [ ] 实现请求重试机制和熔断器模式，提升服务稳定性
- [ ] 集成限流组件（如 Sentinel），防止恶意请求和资源滥用
- [ ] 增加 Agent 服务健康检查，实现自动降级和故障转移


### 2. 对话模块
- [ ] 左侧历史会话列表支持置顶、取消置顶
- [ ] 对话过程中，可以直接终止本次对话
- [ ] 可以对以往发起的对话内容编辑，然后重新对话
- [ ] 对话框集成示例 prompt，用户可以直接选择，并修改填充后即可发起请求

### 3. 用户模块
- [ ] 完善管理员的权限，例如禁用某一用户、用户授权等等
- [ ] 注册时对手机号与密码等级进行校验

### 4. 其他模块
- [ ] 将路线规划结果导出为 h5 页面，然后可以手机扫码展示、调起手机导航
- [ ] 支持跳转至各景点订单服务
- [ ] 地图上单击某个地点后，展示其详细信息（含图片与文字说明）
- [ ] 加一个帮助页面


---

## 📚 相关项目

- **前端项目**：[ai-tourism-frontend](https://github.com/1937983507/ai-tourism-frontend)
- **Python Agent 服务**：[ai-tourism-agent](https://github.com/1937983507/ai-tourism-agent) - 包含所有 AI Agent 相关功能（LangGraph 工作流、工具调用、AI 对话处理等） 
