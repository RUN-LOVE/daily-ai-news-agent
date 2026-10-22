# Daily AI News Agent

一个用 Java 实现的 AI 新闻简报 Agent：每天从 RSS/Atom 新闻源抓取 AI 新闻，按用户关注的话题与关键词筛选，生成简报并保存到站内历史。

核心设计目标是让 **LLM 通过 function calling / ReAct 自行决定调用哪些工具、调用几次、何时结束**，而不是在代码里写死一条新闻处理流水线。

## 环境要求

- JDK 17 或更高版本
- Maven 3.9 或更高版本

## 快速开始

```bash
# 1. 配置 LLM（OpenAI 兼容接口）
# macOS / Linux:
cp .env.example .env
# Windows:
copy .env.example .env
# 然后编辑 .env，至少填写 LLM_BASE_URL、LLM_API_KEY、LLM_MODEL

# 2. 启动服务
mvn spring-boot:run

# 3. 浏览器打开
# http://127.0.0.1:8080
```

首次启动会自动创建 SQLite 数据库 `data/news-agent.db`、建表，并创建一份默认订阅偏好。页面打开后可以直接点击“生成今日简报”。

> 不配置 LLM 也可以启动和保存偏好，但生成简报会返回“LLM 未配置”。API Key 只放在本地 `.env` 或环境变量中，`.env` 已被 Git 忽略。

运行测试：

```bash
mvn test
```

普通的 `mvn test` 不调用外部模型。配置好 `.env` 后可以显式运行真实模型冒烟测试（会产生一次真实调用）：

```bash
mvn test -Dllm.smoke=true
```

## 功能

- 身份信息：称呼、身份/职位、邮箱
- 订阅偏好：关注话题、包含关键词、排除关键词、新闻源、语言、每日条数、推送时间、推送方式
- 新闻源来自可编辑的 `workspace/sources/feeds.json`，支持 RSS 2.0 与 Atom
- Agent 核心：OpenAI 兼容的 LLM 客户端、手写 ReAct 循环、执行轨迹记录
- 六个工具：`list_dir`、`read_file`、`search_content`、`write_file`、`bash`、`fetch_news`
- 生成结果同时写入 `workspace/reports/YYYY-MM-DD.md` 和 SQLite 的 `briefing` 表
- 页面展示今日简报、模型自选的工具执行轨迹、历史简报
- 按用户设置的 `deliverTime` 每日自动生成；应用需保持运行
- 同一天重复运行会覆盖当天结果；定时任务发现当天已有简报则跳过
- 站内历史已实现；邮件推送留作后续扩展

## API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/profile` | 获取用户偏好；首次访问时创建默认偏好 |
| `PUT` | `/api/profile` | 保存用户偏好 |
| `GET` | `/api/sources` | 获取可选的新闻源列表 |
| `POST` | `/api/agent/run` | 立即运行一次 Agent，生成或覆盖今日简报 |
| `GET` | `/api/briefings` | 最近 30 天的简报摘要 |
| `GET` | `/api/briefings/{date}` | 某天的完整简报和 Agent 执行轨迹 |

手动运行：

```bash
curl -X POST http://127.0.0.1:8080/api/agent/run
```

保存偏好示例：

```bash
curl -X PUT http://127.0.0.1:8080/api/profile \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "李雷",
    "role": "算法工程师",
    "email": "lilei@example.com",
    "topics": ["大模型", "AI Agent"],
    "keywords": ["推理", "开源"],
    "excludeKeywords": ["招聘"],
    "sources": ["infoq", "openai-news"],
    "language": "zh",
    "maxItems": 8,
    "deliverTime": "08:30",
    "pushChannel": "INBOX"
  }'
```

## Agent 核心

`AgentRunner` 手写 ReAct 循环：

1. 把系统提示、用户任务和工具定义交给 LLM
2. 模型返回 `tool_calls` 时执行对应工具，把结果以 `role=tool` + `tool_call_id` 追加回消息
3. 再次请求模型，由模型决定继续调用工具还是给出最终回答
4. 达到 `LLM_MAX_STEPS` 轮仍未结束时返回 `MAX_STEPS` 状态

代码里没有“先抓取、再筛选、最后总结”的固定顺序。`BriefingService` 只负责把用户偏好和产出要求写进任务描述，具体用哪个源、抓几次、先读文件还是先抓新闻，都由模型在运行时决定。

六个工具：

| 工具 | 作用 |
| --- | --- |
| `list_dir(path)` | 列出 workspace 内目录的直接子项 |
| `read_file(path)` | 读取 workspace 内的 UTF-8 文本文件（最多 64KB） |
| `search_content(keyword, dir)` | 递归搜索包含关键词的文本行 |
| `write_file(path, content)` | 在 workspace 内写入文本文件 |
| `bash(command)` | 执行受限只读命令（echo、pwd、ls、dir、whoami） |
| `fetch_news(source_ids, keyword?, limit?)` | 抓取 RSS/Atom 新闻源并返回标题、链接、时间和摘要 |

### 安全边界

- 文件路径统一由 `PathGuard` 解析，拒绝绝对路径、`..` 穿越和符号链接逃逸
- `bash` 只允许白名单命令，拒绝管道、重定向、命令替换、换行和绝对路径
- `fetch_news` 不接受任意 URL，只能选择 `feeds.json` 中的新闻源 ID
- 抓取前校验协议、主机和解析后的所有 IP，拒绝回环、内网、链路本地、保留地址和元数据服务地址；重定向逐跳复检
- 新闻响应限制 2MB，单次请求 12 秒超时，单次最多抓取 6 个源
- XML 解析禁用 DOCTYPE 和外部实体，防止 XXE
- 新闻内容被视为不可信数据：系统提示明确要求模型忽略其中的指令，只作为素材
- 工具输出会截断，避免把过长内容塞回模型上下文
- API Key 只从环境变量/本地 `.env` 读取，不写日志，不进入 Git

## 配置

配置通过环境变量或项目根目录的 `.env` 文件提供。

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP 端口，服务只监听 `127.0.0.1` |
| `APP_DB_PATH` | `data/news-agent.db` | SQLite 数据库路径 |
| `APP_WORKSPACE` | `workspace` | Agent 工作目录 |
| `APP_SCHEDULER_ENABLED` | `true` | 是否启用每日定时生成 |
| `LLM_BASE_URL` | 空 | OpenAI 兼容接口地址，例如 `https://api.deepseek.com/v1` |
| `LLM_API_KEY` | 空 | 模型 API Key |
| `LLM_MODEL` | 空 | 模型名称 |
| `LLM_TIMEOUT_SECONDS` | `180` | 单次模型请求超时 |
| `LLM_MAX_STEPS` | `12` | ReAct 最大轮数，上限 30 |

新闻源配置位于 `workspace/sources/feeds.json`，可以按相同结构增删 RSS/Atom 源。

## 数据与目录

```
src/main/java/com/newsagent/
  agent/          ReAct 循环、执行轨迹、OpenAI 兼容客户端
  tool/           工具接口、注册表、路径与命令安全边界
  tool/builtin/   六个工具实现
  news/           RSS/Atom 解析、SSRF URL 校验、受限 HTTP 抓取
  briefing/       简报生成、持久化、历史接口与定时调度
  profile/        用户身份与订阅偏好（Controller / Service / Repository）
  source/         新闻源配置读取
  config/         应用配置与 SQLite DataSource
  common/         统一异常响应
src/main/resources/
  schema.sql      建表 SQL（启动时自动执行）
  static/         单页前端（原生 HTML/CSS/JS）
workspace/sources/feeds.json   新闻源清单
workspace/reports/             Agent 生成的 Markdown 简报
data/                          运行时数据库（Git 忽略）
```

数据库表 `profile` 使用单行设计（`id = 1`），JSON 字段保存话题、关键词和新闻源列表。`briefing` 表按日期唯一保存简报正文、文件路径、模型轮数、停止原因和完整工具调用轨迹。完整建表语句见 `src/main/resources/schema.sql`。

## 测试

- `AgentRunnerTest`：用脚本化的假 LLM 驱动两条不同的工具调用路径，证明执行器不写死顺序
- `BriefingApiIntegrationTest`：假 LLM 走完 `fetch_news → write_file → final`，验证简报落库、历史接口、同日幂等和未配置 LLM 的错误响应
- `RssParserTest` / `UrlGuardTest`：RSS/Atom 解析、XXE 拦截、SSRF 地址拦截
- `FetchNewsToolTest`：新闻源选择、关键词过滤、部分源失败时的降级
- `ToolRegistryTest` / `PathGuardTest` / `CommandGuardTest`：五个必备工具、路径沙箱、命令白名单
- `DeliveryPlannerTest` / `BriefingSchedulerTest`：定时窗口和当天去重

## 技术栈

- Java 17、Spring Boot 3.5
- Spring Web / JDBC / Validation / Scheduling
- SQLite（`sqlite-jdbc`）
- Jackson、Java HttpClient
- 原生 HTML / CSS / JavaScript，无前端构建步骤

## 后续可扩展

- 邮件推送（目前仅站内历史）
- 更精细的新闻去重与相关性排序
- 接入更多新闻源或搜索 API
- 多用户、登录与权限
