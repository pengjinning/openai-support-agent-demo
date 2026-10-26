# Customer Support Agent with Human in the Loop — Spring Boot 版

基于 **Spring Boot 4.1.1 + Spring AI 2.0.1 + DeepSeek** 重写的客服人机协同（HITL, Human-in-the-Loop）Demo。

本项目是 [openai-support-agent-demo](https://github.com/openai/openai-support-agent-demo)（原 NextJS + OpenAI Responses API 版本，后适配智谱 GLM）的 **Java 技术栈全量重写**：前后端均由 Spring Boot 单应用交付，功能与交互对齐原版，规划文档见仓库根目录 [../SPRING_BOOT_REWRITE_PLAN.md](../SPRING_BOOT_REWRITE_PLAN.md)。

![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F)
![Spring AI](https://img.shields.io/badge/Spring_AI-2.0.1-6DB33F)
![DeepSeek](https://img.shields.io/badge/LLM-DeepSeek-4D6BFE)
![JDK](https://img.shields.io/badge/JDK-21-000000)

## 功能特性

- **双视图聊天**：左侧 Customer View（客户视角），右侧 Agent View（坐席视角，含客户信息 / 推荐操作 / 相关文章三个面板），小屏可切换
- **多轮对话**：客户端维护完整会话历史，每轮全量发送
- **流式响应（SSE）**：后端将 DeepSeek 流式输出转换为 OpenAI Responses 风格事件（`response.output_text.delta`、`response.function_call_arguments.delta` 等），前端流式渲染
- **Function Calling（12 个工具）**：流式展示工具名与参数，参数可展开查看
- **建议回复（Suggested message）**：AI 流式生成给坐席的回复草稿，可「Send now」直接发送或「Edit」编辑后发送
- **推荐操作（Recommended actions）**：敏感工具（退款、取消订单等）不会自动执行，而是生成待确认操作，坐席在弹窗中核对参数并 Confirm 后才真正执行 —— 即 HITL 核心
- **本地知识库检索**：`knowledge_base`（11 篇内部文档）+ `faq`（11 篇公开 FAQ）共 22 篇 Markdown，使用纯 Java 实现的 **BM25 词法检索**（DeepSeek 无 Embedding API，故替代原版的向量检索），得分归一化并按 `score > 0.5` 阈值过滤
- **知识库浏览页**：`/kb` 与 `/faq` 文章列表页，支持 `?section=xxx` 高亮定位

## 环境要求

| 依赖 | 版本 |
| ------ | ------ |
| JDK | 21+（本机可用 `/usr/libexec/java_home -v 21` 确认） |
| Maven | 3.9+ |
| DeepSeek API Key | [platform.deepseek.com](https://platform.deepseek.com/) 申请（冒烟测试可不需要） |

## 快速开始

### 1. 配置 API Key

```bash
export DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxxxxx
```

或启动时通过环境变量传入（推荐，避免写入文件）。

### 2. 启动应用

```bash
cd spring-boot-server
mvn spring-boot:run
```

应用启动后访问 <http://localhost:8080/>。

### 3. 无真实 Key 的本地冒烟测试

项目内置一个假 DeepSeek 服务（实现 OpenAI 兼容的流式 `/chat/completions`，含工具调用流式分片），无需真实 API：

```bash
# 终端 1：启动假 DeepSeek（监听 9899）
node scripts/fake-deepseek.mjs

# 终端 2：启动应用并指向假服务
DEEPSEEK_BASE_URL=http://localhost:9899 DEEPSEEK_API_KEY=test mvn spring-boot:run
```

假服务的固定行为：

- **首轮**（无 tool 结果）：输出文本 "Let me check that for you."，随后流式发出 `search_knowledge_base` 工具调用（参数 `{"query":"return policy"}` 分两个分片）
- **后续轮**（历史中含 tool 消息）：输出文本 "Returns can be requested within 30 days."

### 4. 构建

```bash
mvn clean package          # 编译 + 运行测试 + 打包
java -jar target/support-agent-0.1.0.jar
```

## 配置项

[application.yml](src/main/resources/application.yml) 中的全部可配置项（均可用环境变量覆盖）：

| 配置项 | 环境变量 | 默认值 | 说明 |
| -------- | ---------- | -------- | ------ |
| `spring.ai.deepseek.api-key` | `DEEPSEEK_API_KEY` | 空 | DeepSeek API 密钥（必填） |
| `spring.ai.deepseek.base-url` | `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | API 地址（冒烟测试指向本地假服务） |
| `spring.ai.deepseek.chat.model` | `DEEPSEEK_MODEL` | `deepseek-chat` | 模型名（可换 `deepseek-reasoner`） |
| `spring.ai.deepseek.chat.temperature` | — | `0.7` | 采样温度 |
| `app.agent-name` | `AGENT_NAME` | `Blossom` | 坐席人设名字（用于系统提示词与初始消息） |
| `server.port` | — | `8080` | 服务端口 |

## 使用说明（Demo 流程）

1. 打开 <http://localhost:8080/>，左侧为 Customer View（客户提问），右侧为 Agent View（坐席工作台）
2. **知识库问答**：在客户视图问 *"What is the return policy?"* → AI 调用 `search_knowledge_base` → 右侧「Relevant articles」面板展示命中的文章（可点击跳转原文）→ 坐席视图流式出现建议回复
3. **建议回复**：流式生成完毕后出现「Send now / Edit」，Send now 直接以坐席身份发送，Edit 将草稿填入坐席输入框修改后发送
4. **订单类工具（自动执行）**：如 *"What is the status of ORD1001?"* → `get_order` 自动调用并在聊天中显示「Called get order」
5. **敏感操作（人工确认，HITL 核心）**：如 *"Cancel my order"* → AI 生成 `cancel_order` 调用 → 右侧「Recommended actions」出现「cancel order」按钮 → 点击弹出参数确认框 → **Confirm 后才真正执行**，执行结果回传 AI 生成确认回复
6. 点击聊天中的工具条目（⚡ 标记）可展开查看调用参数与返回结果的 JSON

> Demo 数据为内存模拟（客户 Janet Deer / `cus_28X44`，7 笔订单 ORD1001–ORD1007），重启后重置。

## 工具清单（12 个）

| 工具名 | 功能 | 执行模式 |
| -------- | ------ | ---------- |
| `search_knowledge_base` | 检索内部知识库与公开 FAQ | 自动执行 |
| `get_order` | 查询订单详情 | 自动执行 |
| `get_order_history` | 查询用户订单历史 | 自动执行 |
| `create_ticket` | 创建支持工单 | 自动执行 |
| `cancel_order` | 取消订单 | **人工确认** |
| `reset_password` | 发送密码重置邮件 | **人工确认** |
| `send_replacement` | 发送换货 | **人工确认** |
| `create_refund` | 创建退款 | **人工确认** |
| `issue_voucher` | 发放优惠券 | **人工确认** |
| `create_return` | 创建退货 | **人工确认** |
| `create_complaint` | 创建投诉 | **人工确认** |
| `update_info` | 更新用户信息 | **人工确认** |

执行模式定义在 [ToolCatalog.java](src/main/java/com/example/supportagent/tools/ToolCatalog.java)（对应原版 `config/tools-list.ts` 中的 `agentTools`）。

## API 端点

路径与请求/响应 JSON 与原 NextJS 版**完全一致**（含下划线命名）。

| 端点 | 方法 | 说明 |
| ------ | ------ | ------ |
| `/api/turn_response` | POST | AI 流式响应（SSE，Responses 风格事件） |
| `/api/meta` | GET | 前端启动数据（人设、初始消息、客户信息、默认文章、默认操作）★新增 |
| `/api/kb_search` | POST | 知识库 BM25 检索 |
| `/api/list_files?folder=xx` | GET | 列出指定文件夹的 Markdown 文件名 |
| `/api/orders/{order_id}` | GET | 查询订单（不存在返回 404） |
| `/api/orders/{order_id}/cancel` | POST | 取消订单 |
| `/api/orders/{order_id}/create_refund` | POST | 创建退款 |
| `/api/orders/{order_id}/create_return` | POST | 创建退货 |
| `/api/orders/{order_id}/send_replacement` | POST | 发送换货 |
| `/api/users/{user_id}/order_history` | GET | 订单历史 |
| `/api/users/{user_id}/reset_password` | POST | 重置密码 |
| `/api/users/{user_id}/update_info` | POST | 更新用户信息 |
| `/api/complaints/create` | POST | 创建投诉 |
| `/api/tickets/create` | POST | 创建工单 |
| `/api/vouchers/create` | POST | 发放优惠券 |
| `/` `/kb` `/faq` | GET | 页面（聊天主页 / 知识库 / FAQ） |

SSE 快速验证：

```bash
curl -N -X POST http://localhost:8080/api/turn_response \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"What is the return policy?"}]}'
```

## 架构与人机协同流程

```text
浏览器（Thymeleaf 页面 + 原生 JS，由 Spring Boot 提供）
   │  HTTP / SSE（同源）
   ▼
Spring Boot 4.1.1 单应用
   ├─ ChatController ─► AgentService ─► SseAdapter ─► SSE 事件流
   ├─ KnowledgeBaseController（BM25 检索 / 文件列表）
   ├─ Order/User/Complaint/Ticket/Voucher Controllers（模拟业务）
   ├─ PageController + MetaController（页面与启动数据）
   └─ SupportTools（12 个 @Tool，供模型生成调用）
   │  Spring AI 2.0.1（已禁用服务端自动工具执行）
   ▼
DeepSeek API（deepseek-chat）
```

**HITL 流程**：

```text
客户提问 → POST /api/turn_response（带全量历史）
   ├─ 模型输出文本        → 流式建议回复（Send now / Edit）
   ├─ 自动工具（检索/查询）→ 前端调 REST 执行 → 结果作为 function_call_output 回传 → 再次 POST 续写
   └─ 敏感工具            → 「Recommended action」→ 坐席 Confirm → 前端调 REST 执行
                            → 结果作为 assistant 消息回传 → 再次 POST 生成确认回复
```

> **关键设计**：Spring AI 的 `ChatClient` 默认会自动注册 `ToolCallingAdvisor` 在服务端执行工具，这会绕过人工确认环节。本项目在 [AgentService.java](src/main/java/com/example/supportagent/service/AgentService.java) 中通过 `TOOL_CALLING_ADVISOR_AUTO_REGISTER=false` 显式关闭，工具 Schema 仍下发给模型，但执行权完全交给前端 —— 与原版 Demo 的 HITL 语义一致。

## 文件说明

```text
spring-boot-server/
├── pom.xml                                    # Maven 配置（Boot 4.1.1 父 POM + spring-ai-bom 2.0.1 + DeepSeek starter）
├── scripts/
│   └── fake-deepseek.mjs                      # 本地假 DeepSeek 服务（OpenAI 兼容 SSE，含流式工具调用），冒烟测试用
└── src/
    ├── main/java/com/example/supportagent/
    │   ├── SupportAgentApplication.java       # 启动类
    │   ├── config/
    │   │   ├── AppConfig.java                 # 人设常量：系统提示词、初始消息、默认推荐操作（← config/constants.ts）
    │   │   ├── AiConfig.java                  # 将 SupportTools 注册为全局 ToolCallbackProvider
    │   │   └── WebConfig.java                 # 把 knowledge_base/、faq/ 目录暴露为静态资源（→ /knowledge_base/*.md）
    │   ├── controller/
    │   │   ├── ChatController.java            # POST /api/turn_response（StreamingResponseBody 输出 SSE）
    │   │   ├── KnowledgeBaseController.java   # /api/kb_search、/api/list_files
    │   │   ├── OrderController.java           # /api/orders/**（查询/取消/退款/退货/换货）
    │   │   ├── UserController.java            # /api/users/**（订单历史/重置密码/更新信息）
    │   │   ├── ComplaintController.java       # /api/complaints/create
    │   │   ├── TicketController.java          # /api/tickets/create
    │   │   ├── VoucherController.java         # /api/vouchers/create
    │   │   ├── MetaController.java            # GET /api/meta（前端启动数据）
    │   │   └── PageController.java            # /、/kb、/faq 页面路由
    │   ├── model/
    │   │   ├── demo/DemoData.java             # 内存演示数据：客户、订单×7、用户信息、默认文章（← config/demoData.ts）
    │   │   └── kb/KBSearchResult.java         # 检索结果结构（与原 /api/kb_search 响应字段一致）
    │   ├── service/
    │   │   ├── AgentService.java              # 核心：历史→Spring AI 消息转换、DeepSeek 流式调用、SSE 输出
    │   │   ├── SseAdapter.java                # ChatResponse 流 → Responses 风格 SSE 事件（← lib/zhipu-stream.ts）
    │   │   ├── KnowledgeBaseService.java      # BM25 检索 + 文件列表（← lib/kb-search.ts）
    │   │   └── SupportDataService.java        # 各业务端点的模拟逻辑（← app/api/**/route.ts）
    │   ├── tools/
    │   │   ├── SupportTools.java              # 12 个 @Tool 方法定义（名称/描述/参数，← config/tools-list.ts）
    │   │   └── ToolCatalog.java               # 工具敏感级分类：是否需要人工确认（agentTools）
    │   └── util/
    │       └── Json.java                      # Jackson 3（tools.jackson）序列化助手
    └── main/resources/
        ├── application.yml                    # 全部配置项（见上表）
        ├── templates/
        │   ├── index.html                     # 聊天主页（双视图 + 上下文面板 + 确认弹窗）
        │   ├── kb.html                        # 内部知识库列表页
        │   └── faq.html                       # 公开 FAQ 列表页
        ├── static/
        │   ├── css/app.css                    # 样式（珊瑚红主题、气泡、面板、弹窗）
        │   ├── js/chat.js                     # 前端编排核心（← lib/assistant.ts + stores + React 视图）
        │   │                                  #   SSE 解析、工具执行、推荐操作确认、建议回复、打字指示器
        │   └── js/articles.js                 # 文章列表页逻辑（← components/ListArticles.tsx）
        ├── knowledge_base/*.md                # 内部知识库 11 篇（复制自 ../public/knowledge_base）
        └── faq/*.md                           # 公开 FAQ 11 篇（复制自 ../public/faq）
```

### 与原 NextJS 版的对应关系

| 原文件 | 新文件 |
| -------- | -------- |
| `lib/assistant.ts`（processMessages 事件编排） | [static/js/chat.js](src/main/resources/static/js/chat.js) |
| `lib/zhipu-stream.ts`（Chat Completions → Responses 事件适配） | [SseAdapter.java](src/main/java/com/example/supportagent/service/SseAdapter.java)（改为 Spring AI 流 → Responses 事件） |
| `lib/kb-search.ts`（向量检索） | [KnowledgeBaseService.java](src/main/java/com/example/supportagent/service/KnowledgeBaseService.java)（改为 BM25） |
| `lib/tools/tools-handling.ts`（工具执行/建议） | chat.js 中的 `executeTool` + `ToolCatalog` |
| `config/tools-list.ts` | [SupportTools.java](src/main/java/com/example/supportagent/tools/SupportTools.java) + [ToolCatalog.java](src/main/java/com/example/supportagent/tools/ToolCatalog.java) |
| `config/functions.ts`（fetch 各 API） | chat.js 中的 `executeTool`（fetch 各 REST 端点） |
| `config/constants.ts` / `demoData.ts` | [AppConfig.java](src/main/java/com/example/supportagent/config/AppConfig.java) / [DemoData.java](src/main/java/com/example/supportagent/model/demo/DemoData.java) |
| `app/api/**/route.ts`（14 个端点） | `controller/` 下各 Controller + [SupportDataService.java](src/main/java/com/example/supportagent/service/SupportDataService.java) |
| `components/*`（React 组件） | `templates/index.html` + `static/js/chat.js` + `app.css` |
| `app/page.tsx`、`app/kb`、`app/faq` | [PageController.java](src/main/java/com/example/supportagent/controller/PageController.java) + `templates/*.html` |
| `scripts/fake-zhipu.mjs` | [scripts/fake-deepseek.mjs](scripts/fake-deepseek.mjs) |
| `public/knowledge_base`、`public/faq` | `src/main/resources/knowledge_base`、`faq`（检索源 + 静态服务二合一） |

## 测试

```bash
mvn test    # 运行 KnowledgeBaseServiceTest（检索相关性、阈值过滤、文件列表、工具分类）
```

## License

MIT（与上游仓库一致）。
