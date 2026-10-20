# Daily AI News Agent

一个用 Java 实现的 AI 新闻简报 Agent：每天聚合 AI 新闻，按用户关注的话题与关键词筛选，生成简报并推送。

核心设计目标是让 **LLM 通过 function calling / ReAct 自行决定调用哪些工具、调用几次、何时结束**，而不是在代码里写死一条新闻处理流水线。

> 当前进度：Day 2，已完成工程骨架、身份与订阅偏好、以及 LLM 驱动的 ReAct 循环和五个基础工具。新闻抓取、简报生成和前端执行轨迹将在后续迭代接入。

## 环境要求

- JDK 17 或更高版本
- Maven 3.9 或更高版本

## 快速开始

```bash
# 1. 启动服务
mvn spring-boot:run

# 2. 浏览器打开
# http://127.0.0.1:8080
```

首次启动会自动创建 SQLite 数据库 `data/news-agent.db` 并初始化表结构，无需手动建库。页面打开后会自动创建一份默认偏好，按需修改并保存即可。

运行测试：

```bash
mvn test
```

## 当前功能

- 填写身份信息：称呼、身份/职位、邮箱
- 设置订阅偏好：关注话题、包含关键词、排除关键词、新闻源、语言、每日条数、推送时间、推送方式
- 新闻源来自可编辑的 `workspace/sources/feeds.json`
- 保存后写入 SQLite，刷新页面仍然保留
- 输入校验与友好的错误提示（如未知新闻源、选择邮件推送但未填邮箱）
- Agent 核心：OpenAI 兼容的 LLM 客户端、手写 ReAct 循环、执行轨迹记录
- 五个基础工具：`list_dir`、`read_file`、`search_content`、`write_file`、`bash`
- 所有文件工具限制在 `workspace/` 内，`bash` 使用命令白名单并拦截路径穿越

## API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/profile` | 获取用户偏好；首次访问时创建默认偏好 |
| `PUT` | `/api/profile` | 保存用户偏好 |
| `GET` | `/api/sources` | 获取可选的新闻源列表 |

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
    "sources": ["jiqizhixin", "openai-news"],
    "language": "zh",
    "maxItems": 8,
    "deliverTime": "08:30",
    "pushChannel": "INBOX"
  }'
```

## 配置

配置通过环境变量或项目根目录的 `.env` 文件提供（`.env` 已被 Git 忽略，不会提交密钥）。

```bash
cp .env.example .env
```

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP 端口，服务只监听 `127.0.0.1` |
| `APP_DB_PATH` | `data/news-agent.db` | SQLite 数据库路径 |
| `APP_WORKSPACE` | `workspace` | Agent 工作目录 |
| `LLM_BASE_URL` | 空 | OpenAI 兼容接口地址，例如带 `/v1` 的 base URL |
| `LLM_API_KEY` | 空 | 模型 API Key，只放在 `.env` 或环境变量中 |
| `LLM_MODEL` | 空 | 模型名称 |
| `LLM_TIMEOUT_SECONDS` | `180` | 单次模型请求超时 |
| `LLM_MAX_STEPS` | `12` | ReAct 最大轮数，上限 30 |

新闻源配置位于 `workspace/sources/feeds.json`，可以按相同结构增删 RSS 源。

## Agent 核心

`AgentRunner` 手写 ReAct 循环：把系统提示、用户任务、工具定义交给 LLM；模型返回 `tool_calls` 时执行工具并把结果追加回消息，再次请求模型，直到模型给出最终回答或达到最大轮数。代码里没有写死“先搜索、再总结”之类的固定顺序。

五个工具：

| 工具 | 作用 |
| --- | --- |
| `list_dir(path)` | 列出 workspace 内目录的直接子项 |
| `read_file(path)` | 读取 workspace 内的 UTF-8 文本文件（最多 64KB） |
| `search_content(keyword, dir)` | 递归搜索包含关键词的文本行 |
| `write_file(path, content)` | 在 workspace 内写入文本文件 |
| `bash(command)` | 执行受限只读命令（echo、pwd、ls、dir、whoami） |

安全边界：

- 路径统一由 `PathGuard` 解析，拒绝绝对路径、`..` 穿越和符号链接逃逸
- `bash` 只允许白名单命令，拒绝管道、重定向、命令替换和绝对路径
- 工具输出、文件读取、命令执行都有大小或超时限制

配置好 `.env` 后，可以显式运行一次真实模型冒烟测试：

```bash
mvn test -Dllm.smoke=true
```

普通的 `mvn test` 不会调用外部模型，使用脚本化假 LLM 验证“模型决定工具顺序”的循环逻辑。

## 数据与目录

```
src/main/java/com/newsagent/
  agent/          ReAct 循环、执行轨迹、OpenAI 兼容客户端
  tool/           工具接口、注册表、路径与命令安全边界
  tool/builtin/   五个基础工具实现
  profile/        用户身份与订阅偏好（Controller / Service / Repository）
  source/         新闻源配置读取
  config/         应用配置与 SQLite DataSource
  common/         统一异常响应
src/main/resources/
  schema.sql      建表 SQL（启动时自动执行）
  static/         单页前端（原生 HTML/CSS/JS）
workspace/sources/feeds.json   新闻源清单
workspace/reports/             Agent 生成的简报输出目录
data/                          运行时数据库（Git 忽略）
```

数据库表 `profile` 使用单行设计（`id = 1`），JSON 字段保存话题、关键词和新闻源列表。完整建表语句见 `src/main/resources/schema.sql`。

## 技术栈

- Java 17、Spring Boot 3.5
- Spring Web / JDBC / Validation
- SQLite（`sqlite-jdbc`）
- Jackson、Java HttpClient
- 原生 HTML / CSS / JavaScript，无前端构建步骤

## 后续计划

- 接入 RSS / Bing News 抓取工具，把用户偏好注入提示词
- 生成并持久化每日简报，提供手动触发接口
- 页面展示 Agent 执行轨迹、今日简报和历史简报
- 每日定时执行、去重与可选邮件推送
