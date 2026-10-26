# Spring Boot 重写规划文档

> 目标：参考当前 [openai-support-agent-demo](https://github.com/openai/openai-support-agent-demo) 的全部功能，
> 使用 **Spring Boot 4.1.1 + Spring AI 2.0.1 + Spring AI DeepSeek starter** 重写服务端，
> 在保持产品功能与交互不变的前提下，用 Java 技术栈替换原 NextJS 服务端。

---

## 1. 目标与范围

### 1.1 目标
1. 用 Spring Boot 4.1.1 重写所有**服务端能力**（AI 编排、流式响应、工具调用、知识库检索、演示数据 API）。
2. AI 模型从 Zhipu GLM 切换为 **DeepSeek**，通过 Spring AI 2.0.1 的 `DeepSeek` starter 接入。
3. 保留完整的「人机协同（Human-in-the-loop）」交互：客户视图 + 坐席视图、建议回复、推荐操作确认、知识库面板。
4. 前端一并改造为 **Spring Boot 单应用交付**（Thymeleaf 模板 + 原生 JS + 静态资源），不再依赖 NextJS；SSE 事件契约保持与原客户端一致，便于移植交互逻辑。

### 1.2 不在本阶段范围
- 不引入真实数据库 / 消息队列（沿用演示数据的内存模拟）。
- 不做鉴权、多租户、生产化部署（后续可扩展）。
- UI 样式以功能对齐为准（复刻核心交互，不追求像素级还原原 React 视觉）。

---

## 2. 现有功能盘点（重写清单）

### 2.1 核心功能
| # | 功能 | 说明 | 重写归属 |
|---|------|------|----------|
| 1 | 双视图聊天 | Customer View / Agent View 切换 | 前端复用 |
| 2 | 多轮对话 | 客户端维护会话，每轮全量发送历史 | 后端 `/api/turn_response` |
| 3 | 流式响应（SSE） | Responses 风格事件流 | 后端 SSE 适配器 |
| 4 | 系统提示词（人设） | 坐席 "Blossom" 的开发者提示词 | 后端常量 |
| 5 | Function calling | 12 个工具，流式参数 | 后端工具注册 + 流式映射 |
| 6 | 建议回复（Suggested message） | 流式生成，可「Send now / Edit」 | 后端流式 + 前端复用 |
| 7 | 推荐操作（Recommended actions） | 敏感工具需人工确认后执行 | 前端编排 + 后端 REST |
| 8 | 相关知识文章面板 | 知识库检索结果展示 | 后端 `/api/kb_search` |
| 9 | 客户信息面板 | 静态演示数据 | 后端配置数据 |
| 10 | KB / FAQ 文章列表页 | `/kb`、`/faq` 页面 | 前端复用 + 静态资源 |

### 2.2 工具清单（12 个）
| 工具名 | 参数 | 执行模式 |
|--------|------|----------|
| `search_knowledge_base` | query | 自动执行 |
| `get_order` | order_id | 自动执行 |
| `get_order_history` | user_id | 自动执行 |
| `create_ticket` | user_id, type, details, order_id | 自动执行 |
| `cancel_order` | order_id | 人工确认 |
| `reset_password` | user_id | 人工确认 |
| `send_replacement` | product_id, order_id | 人工确认 |
| `create_refund` | order_id, amount, reason | 人工确认 |
| `issue_voucher` | user_id, amount, reason | 人工确认 |
| `create_return` | order_id, product_ids | 人工确认 |
| `create_complaint` | user_id, type, details, order_id | 人工确认 |
| `update_info` | user_id, info{field,value} | 人工确认 |

> 执行模式语义：自动执行工具由客户端在 AI 循环中直接调用后端 REST 执行；
> 人工确认工具先被标记为「推荐操作」，坐席点击 Confirm 后才调用后端 REST 执行。
> 该编排逻辑位于客户端，后端只需提供工具 Schema 与对应 REST 端点。

### 2.3 API 端点清单（原 NextJS 路由 → Spring Boot 映射）
| # | 原端点 | 方法 | 新 Spring 端点 | 说明 |
|---|--------|------|----------------|------|
| 1 | `/api/turn_response` | POST | `/api/turn_response` | SSE 流式 AI 响应 |
| 2 | `/api/kb_search` | POST | `/api/kb_search` | 知识库检索 |
| 3 | `/api/list_files` | GET | `/api/list_files` | 列出指定目录文件 |
| 4 | `/api/orders/{order_id}` | GET | `/api/orders/{order_id}` | 查询订单 |
| 5 | `/api/orders/{order_id}/cancel` | POST | 同上 | 取消订单 |
| 6 | `/api/orders/{order_id}/create_refund` | POST | 同上 | 创建退款 |
| 7 | `/api/orders/{order_id}/create_return` | POST | 同上 | 创建退货 |
| 8 | `/api/orders/{order_id}/send_replacement` | POST | 同上 | 发送换货 |
| 9 | `/api/users/{user_id}/order_history` | GET | 同上 | 订单历史 |
| 10 | `/api/users/{user_id}/reset_password` | POST | 同上 | 重置密码 |
| 11 | `/api/users/{user_id}/update_info` | POST | 同上 | 更新用户信息 |
| 12 | `/api/complaints/create` | POST | 同上 | 创建投诉 |
| 13 | `/api/tickets/create` | POST | 同上 | 创建工单 |
| 14 | `/api/vouchers/create` | POST | 同上 | 发放优惠券 |

> 保持端点路径（含下划线）与请求/响应 JSON 结构**完全一致**，前端零改动。

---

## 3. 技术栈与版本

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 21（LTS） | Spring Boot 4 基线为 Java 17，推荐 21 |
| Spring Boot | 4.1.1 | 父 POM |
| Spring AI | 2.0.1 | 引入 `spring-ai-bom` |
| Spring AI DeepSeek starter | 2.0.1 | 模型接入 |
| 模板引擎 | Thymeleaf | 前端页面渲染 |
| 构建工具 | Maven | 依赖坐标清晰 |
| 前端 | Spring Boot 内静态资源 + 原生 JS | 单应用交付，不再依赖 NextJS |

### 3.1 依赖坐标（Maven）
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.1</version>
    <relativePath/>
</parent>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>2.0.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <!-- Spring AI DeepSeek 接入 -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-deepseek</artifactId>
    </dependency>
    <!-- 前端页面渲染 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
</dependencies>
```

> ⚠️ **待确认（构建期核实）**：Spring AI 2.0 存在 starter 重命名的情况，需在实现时以
> `spring-ai-bom:2.0.1` 实际可解析的 artifactId 为准（候选：`spring-ai-starter-model-deepseek`
> 或新命名 `spring-ai-deepseek-spring-boot-starter`）。实现第一步将先验证依赖可解析性。

### 3.2 配置（application.yml）
```yaml
spring:
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      chat:
        base-url: https://api.deepseek.com
        model: deepseek-chat          # 默认；可切换 deepseek-reasoner
        temperature: 0.7
```
- 环境变量 `DEEPSEEK_API_KEY`（对应原 `ZHIPUAI_API_KEY`）。
- 前端 NextJS `.env` 中 `ZHIPU_MODEL` / `ZHIPU_BASE_URL` 等配置项废弃，统一由 Spring 侧管理。

---

## 4. 总体架构

```
┌───────────────────── 浏览器 ─────────────────────────┐
│  Thymeleaf 页面 + 原生 JS（由 Spring Boot 提供）      │
│  ├─ /           双视图聊天（Customer / Agent）        │
│  ├─ /kb、/faq   文章列表页                            │
│  └─ JS：SSE 流式解析、工具执行编排、推荐操作确认      │
└───────────────┬───────────────────────────────────────┘
                │ HTTP / SSE（同源，无跨域）
┌───────────────▼───────────────────────────────────────┐
│  Spring Boot 4.1.1 单应用（重写全部）                  │
│  ├─ ChatController      POST /api/turn_response       │
│  │    └─ AgentService（AI 编排 + SSE 适配）           │
│  ├─ KnowledgeBaseController /api/kb_search 等         │
│  ├─ Order/User/Complaint/Ticket/Voucher Controllers    │
│  ├─ PageController（/、/kb、/faq 页面）               │
│  ├─ Demo 数据（内存 records）                          │
│  └─ Tool 注册（@Tool，12 个）                         │
└───────────────┬───────────────────────────────────────┘
                │ Spring AI 2.0.1
        ┌───────▼────────┐
        │  DeepSeek API   │  (https://api.deepseek.com)
        └────────────────┘
```

**关键原则：后端无会话状态。** 客户端每轮携带完整对话历史，后端只做单次流式生成，与现有实现一致。

---

## 5. 模块设计

### 5.1 建议包结构
```
com.example.supportagent
├── SupportAgentApplication.java
├── config
│   ├── AiConfig.java                 # ChatClient / DeepSeekChatModel bean
│   ├── AppProperties.java            # 人设、模型等常量（@ConfigurationProperties）
│   └── WebConfig.java                # CORS / 静态资源
├── model
│   ├── dto/                          # 请求/响应 DTO（conversation items、SSE 事件）
│   ├── demo/                         # Demo 数据 records：Order、UserInfo、Customer
│   └── kb/                           # KnowledgeBaseSearchResult
├── tools
│   ├── SupportTools.java             # 12 个 @Tool 方法（Schema 供模型使用）
│   └── ToolCatalog.java              # 工具名 → 是否「需人工确认」分类
├── service
│   ├── AgentService.java             # 核心 AI 编排 + 流式响应
│   ├── SseAdapter.java               # Spring AI 流 → Responses 风格 SSE 事件
│   └── KnowledgeBaseService.java     # 本地知识库检索
├── controller
│   ├── ChatController.java
│   ├── KnowledgeBaseController.java
│   ├── OrderController.java
│   ├── UserController.java
│   ├── ComplaintController.java
│   ├── TicketController.java
│   ├── VoucherController.java
│   └── PageController.java          # /、/kb、/faq 页面路由
└── resources
    ├── application.yml
    ├── templates/
    │   ├── index.html               # 双视图聊天主页
    │   ├── kb.html                  # 内部知识库列表
    │   └── faq.html                 # FAQ 列表
    ├── static/
    │   ├── css/app.css
    │   ├── js/chat.js               # SSE 流式解析、工具编排、推荐操作
    │   └── js/articles.js           # 文章列表加载
    ├── knowledge_base/*.md           # 11 篇内部知识库
    └── faq/*.md                      # 11 篇 FAQ
```

### 5.2 领域模型与 Demo 数据
将原 `config/demoData.ts` 迁移为 Java records（`@ConfigurationProperties` 或静态常量）：
- `CustomerDetails`：name=`Janet Deer`、id=`cus_28X44`、orderNb=8、signupDate=2023-11-28。
- `UserInfo`：name/email/phone/address/order_history（7 个订单号）。
- `DemoOrder`：7 条订单，含 status/items/tracking/cancellation_reason/refund 等字段（动态日期逻辑改为 Java 实现）。
- 常量：`AGENT_NAME = "Blossom"`、`DEVELOPER_PROMPT`（人设提示词）、`INITIAL_MESSAGE`、`DEFAULT_ACTION`（create_ticket）。

### 5.3 工具注册（@Tool）
将原 `config/tools-list.ts` 的 12 个工具迁移为 Spring AI `@Tool` 方法，参数描述、类型、枚举用 `@ToolParam` 表达：
```java
@Component
public class SupportTools {
    @Tool(description = "Search the internal knowledge base and public FAQ ...")
    public List<KBSearchResult> searchKnowledgeBase(
            @ToolParam(description = "Search query ...") String query) { ... }

    @Tool(description = "Order ID to cancel") // 示例：getOrder、cancelOrder 等
    public String cancelOrder(@ToolParam(description = "Order ID to cancel") String orderId) { ... }
    // ...其余 10 个工具
}
```
- 工具 Schema（名称/描述/参数/枚举）由 Spring AI 根据注解自动生成，传入 ChatClient 供 DeepSeek 使用。
- **重要**：后端只注册 Schema，不执行 Spring AI 的自动工具调用；工具的实际执行由客户端调用 REST 端点完成（与原架构一致，保留 HITL 语义）。

### 5.4 AI 编排服务 + SSE 适配器
`AgentService` 核心流程（对应原 `lib/assistant.ts` + `lib/zhipu-stream.ts`）：
1. 接收客户端对话历史（`messages` + `tools`，含 developer/user/assistant/function_call/function_call_output 项）。
2. 将 Responses 风格条目转换为 Spring AI `Message`（System/User/Assistant/ToolResponse），逻辑对齐原 `toChatMessages()`：
   - `developer` → `SystemMessage`
   - `function_call` → 携带 toolCalls 的 `AssistantMessage`
   - `function_call_output` → `ToolResponseMessage`
   - 相邻纯文本 assistant 消息合并
3. 调用 DeepSeek 流式接口：
   ```java
   chatClient.prompt()
       .system(DEVELOPER_PROMPT)
       .messages(history)
       .tools(toolCallbacks)
       .stream()
       .chatResponse();   // Flux<ChatResponse>
   ```
4. 将 `Flux<ChatResponse>` 映射为 Responses 风格 SSE 事件，输出 `data: {"event":..., "data":...}\n\n`。

**SSE 事件映射表**（SseAdapter，保持客户端零改动）：

| Spring AI 流增量 | 客户端事件 |
|------------------|-----------|
| 文本 delta | `response.output_text.delta` |
| 文本结束 | `response.output_text.done` + `response.output_item.done`(message) |
| 工具调用首次出现 | `response.output_item.added`(function_call, in_progress) |
| 工具参数 delta | `response.function_call_arguments.delta` |
| 工具参数结束 | `response.function_call_arguments.done` + `response.output_item.done`(function_call, completed) |

> DeepSeek 推理模型可能输出 `reasoning_content`，按原实现**忽略**推理增量，仅输出可见 content。

### 5.5 REST Controller 映射
14 个端点按第 2.3 节映射表实现，请求/响应 JSON 与原端点逐字段一致（如退款返回
`{message, reason}`，更新信息返回 `{message, updatedField, newValue}` 等）。演示数据为内存模拟，
不落库。

### 5.6 知识库搜索
原实现依赖 Zhipu `embedding-3`；**DeepSeek 不提供 Embedding API**，需替换检索方案：

- **方案 A（推荐，默认）**：本地词法检索（TF-IDF / BM25 + 关键词加权），纯 Java 实现，零额外依赖，
  保持「本地知识库、无需向量库初始化」的产品体验。
  - 读取 `resources/knowledge_base` 与 `resources/faq` 下所有 `.md`。
  - 分词（英文按空格/标点）→ BM25 评分 → 归一化到 [0,1] → 过滤 `score > 0.5` → TopK=6。
  - 返回结构与原 `/api/kb_search` 完全一致：`{results:[{text, score, attributes:{type, filename, filepath}}]}`。
- **方案 B（可选增强）**：通过 Spring AI 引入 Embedding 模型（如 OpenAI / Ollama），
  恢复向量余弦相似度，需额外 API key 或本地模型。

### 5.7 前端（Thymeleaf + 原生 JS）
单应用交付，页面由 Spring Boot 直接提供：
- `/`：双视图聊天主页。上方小屏切换开关，左 Customer View、右 Agent View（坐席视图含
  客户信息、推荐操作、相关文章三个面板）。核心交互由 `static/js/chat.js` 实现：
  - 使用 `fetch` 读取 `/api/turn_response` 的流式响应，按 SSE 事件（`response.output_text.delta`、
    `response.output_item.added`、`response.function_call_arguments.delta` 等）更新 DOM。
  - 维护对话历史（developer/user/assistant/function_call/function_call_output），
    自动工具在收到 `function_call` 事件后调用对应 REST 端点执行，结果回填为 `function_call_output`，
    再次请求 `/api/turn_response` 递归生成回复（对齐原 `processMessages` 编排）。
  - 敏感工具展示为「Recommended action」，坐席在弹窗中 Confirm 后调用 REST 执行。
  - 建议回复（Suggested message）流式展示，并提供「Send now / Edit」。
- `/kb`、`/faq`：由 `static/js/articles.js` 调用 `/api/list_files` 拉取文章列表并渲染。
- 样式：`static/css/app.css` 提供与原 Demo 一致的珊瑚红主题与气泡布局（功能对齐）。
- KB/FAQ 的 `.md` 文件由 Spring Boot 静态资源提供（`/knowledge_base/*.md`、`/faq/*.md`），
  供文章页展示与检索服务读取。

---

## 6. 人机协同（HITL）流程

```
客户提问
  └─ 前端调用 POST /api/turn_response（携带历史）
        └─ 后端流式返回：文本 + function_call 事件
              ├─ 自动工具（如 search_knowledge_base / get_order）
              │     └─ 前端调用对应 REST 执行 → 结果作为 function_call_output 追加
              │         └─ 再次 POST /api/turn_response → 生成最终回复
              └─ 人工确认工具（如 cancel_order）
                    └─ 前端展示「Recommended action」
                          └─ 坐席点击 Confirm → 前端调用对应 REST 执行
                                └─ 结果作为 assistant 消息追加 → 再次 POST → 生成确认回复
```

---

## 7. 实施步骤（任务分解）

| 阶段 | 任务 | 产出/验收 |
|------|------|-----------|
| 0 | 初始化 Spring Boot 工程、验证依赖可解析（BOM/DeepSeek starter 坐标） | `mvn spring-boot:run` 可启动 |
| 1 | 配置 DeepSeek（application.yml + ApiKey），跑通一次简单对话 | 控制台/接口返回模型文本 |
| 2 | 迁移常量与 Demo 数据（人设、客户、订单、用户） | Java records 齐全 |
| 3 | 实现 12 个工具 Schema（@Tool） | 工具 Schema 正确生成 |
| 4 | 实现 `AgentService` + `SseAdapter`（对话历史转换 + 流式 SSE） | `/api/turn_response` 流式输出与前端兼容 |
| 5 | 实现 13 个 REST 端点（订单/用户/投诉/工单/优惠券/列表/检索） | 各端点响应与原一致 |
| 6 | 实现知识库检索服务（BM25）+ `/api/kb_search` | 检索结果结构一致、阈值过滤正确 |
| 7 | 实现 Thymeleaf 前端（`/`、`/kb`、`/faq` + JS 编排） | 客户/坐席双视图完整可用 |
| 8 | 端到端验证（示例问题 + 工具执行 + HITL 确认） | 功能对齐验收清单 |

---

## 8. 验证计划
1. **构建**：`mvn clean package` 通过。
2. **接口验证**：对 14 个端点逐一 curl 校验状态码与 JSON 结构。
3. **流式验证**：curl `/api/turn_response` 观察 SSE 事件序列（文本 delta → function_call → 文本 done）。
4. **端到端**：前端双视图跑通以下示例：
   - 知识库问题（"What is the return policy?"）→ 触发 `search_knowledge_base` → 显示相关文章与建议回复。
   - 订单查询（"What is the status of ORD1001?"）→ `get_order` 自动执行。
   - 敏感操作（"Cancel my order"）→ 生成 `cancel_order` 推荐操作 → 坐席 Confirm → 执行成功。
5. **HITL 语义**：敏感工具不会自动执行，必须经推荐操作确认。

---

## 9. 风险与待确认事项

| # | 事项 | 风险 | 建议 |
|---|------|------|------|
| 1 | Spring AI 2.0.1 DeepSeek starter 实际 artifactId（重命名） | 中 | 实现第 0 步先验证，以可解析坐标为准 |
| 2 | Spring AI 2.0 流式工具调用参数 delta 的 API 形态 | 中 | 实现第 4 步以实际 `ChatResponse` 结构为准，必要时解析完整 toolCalls |
| 3 | DeepSeek 无 Embedding API | 中 | 采用 BM25 方案（已确认） |

---

## 10. 已确认的关键决策

| 决策项 | 结论 |
|--------|------|
| 前端策略 | **port**：前端也改造，由 Spring Boot 提供静态资源 / Thymeleaf 页面（单应用交付） |
| 知识库检索方案 | **bm25**：本地词法检索（TF-IDF / BM25），零额外依赖 |
| 项目位置 | **subdir**：当前仓库内新建 `spring-boot-server/` 子目录 |
| 构建工具 | **maven**：Maven |
