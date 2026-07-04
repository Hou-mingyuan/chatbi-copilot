# 架构设计

## 1. 总体架构

ChatBI Copilot 采用「前后端分离 + 可插拔 LLM」的架构：前端负责交互与可视化，后端负责
Schema 感知的提示词组装、LLM 调用、SQL 安全护栏、查询执行与图表推荐。应用自身的元数据
（数据源、历史、收藏、语义层）存储在内嵌 H2 中，做到零外部依赖即可启动。

```mermaid
flowchart LR
  User([用户]) --> FE["前端 (Vue3 + ECharts)"]
  FE -->|"REST /api"| BE

  subgraph BE["后端 (Spring Boot 3)"]
    QC[QueryController] --> T2S[Text2SqlService]
    T2S --> PB["PromptBuilder<br/>Schema + 语义层"]
    T2S --> LLM["LlmClient<br/>OpenAI 兼容"]
    T2S --> GUARD["SqlGuard<br/>只读安全护栏"]
    T2S --> EXE[SqlExecutor]
    EXE --> CHART[ChartRecommender]
    T2S --> HIST[HistoryService]
    PB --> INS[SchemaInspector]
  end

  INS --> TGT[("目标业务库<br/>MySQL / PostgreSQL")]
  EXE --> TGT
  LLM --> PROV[["LLM 供应商<br/>DeepSeek / OpenAI / 通义 / Ollama"]]
  BE --> META[("H2 元数据库<br/>数据源 / 历史 / 收藏 / 语义")]
```

## 2. 问数请求时序

```mermaid
sequenceDiagram
  autonumber
  participant U as 用户
  participant F as 前端
  participant B as 后端
  participant L as LLM
  participant D as 目标库

  U->>F: 输入自然语言问题
  F->>B: POST /api/query/ask
  B->>B: 读取 Schema + 语义层，组装 Prompt
  B->>L: /chat/completions
  L-->>B: JSON { sql, explanation, needClarification }
  alt 需要澄清
    B-->>F: 返回澄清问题（不执行）
  else 正常
    B->>B: SqlGuard 校验（仅 SELECT / 注入 LIMIT / 拦截危险）
    B->>D: 执行只读 SELECT
    D-->>B: 结果集
    B->>B: 图表类型推荐 + 记录历史
    B-->>F: 结果 + SQL + 图表配置
    F->>U: 表格 / ECharts 渲染
  end
```

## 3. SQL 安全护栏

只读护栏是本项目的安全核心，采用「解析优先信任 + 正则兜底」的双层策略：

```mermaid
flowchart TD
  A[候选 SQL] --> B[清洗: 去除 markdown/尾分号]
  B --> C{多语句?}
  C -- 是 --> R[拒绝]
  C -- 否 --> D{危险构造?<br/>OUTFILE/sleep/load_file}
  D -- 是 --> R
  D -- 否 --> E{以 SELECT/WITH 开头?}
  E -- 否 --> R
  E -- 是 --> F[JSqlParser 解析]
  F -- 成功 --> G{是 Select?}
  G -- 否 --> R
  G -- 是 --> H[AST 注入/收敛 LIMIT]
  F -- 失败 --> I{含 DML/DDL 关键字?}
  I -- 是 --> R
  I -- 否 --> J[字符串方式补 LIMIT]
  H --> K[安全 SQL]
  J --> K
```

额外的纵深防御：
- 目标库连接池强制 `read-only`；
- `Statement.setMaxRows` 与 `queryTimeout` 双重限制；
- LIMIT 默认 500、上限 5000（可配置）。

## 4. 模块划分（后端）

| 包 | 职责 |
| --- | --- |
| `datasource` | 数据源 CRUD、连接池管理、Schema/注释读取 |
| `semantic` | 表/字段业务别名与描述，增强 Schema |
| `llm` | OpenAI 兼容客户端，供应商可配置切换 |
| `text2sql` | 提示词组装、SQL 护栏、执行器、编排 |
| `chart` | 结果集图表类型推荐 |
| `history` | 查询历史、收藏 |
| `export` | 结果导出 Excel |
| `config` / `common` | 配置属性、统一响应、异常、加密 |

## 5. 数据存储

- **应用元数据**：内嵌 H2（文件模式，`./data/chatbi`），随应用启动自动建表，零配置。
- **目标业务数据**：用户在「数据源」中配置的 MySQL / PostgreSQL，仅只读访问。
- **密码安全**：数据源密码使用 AES-GCM 加密后存储，密钥由 `CHATBI_SECRET` 派生。
