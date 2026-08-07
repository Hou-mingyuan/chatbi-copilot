# Changelog

本项目的所有重要变更均记录在此文件。格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added

- 服务端可撤销会话、CSRF、登录限速、ADMIN / ANALYST / VIEWER 与用户管理。
- 数据源、表、敏感列、导出和语义管理 ACL；管理员权限矩阵和审计查询。
- MySQL / PostgreSQL 数据库账号只读核验与方言化 EXPLAIN 风险分析。
- TABLE / COLUMN / METRIC / ENUM / TIME / JOIN 语义定义、版本快照和 Prompt 预览。
- 持久化异步查询任务、真实阶段、取消、总超时、风险确认和重试。
- 服务端多轮会话、确定性澄清/拒绝与结果数据解读。
- 带 SHA-256 的结果快照；历史、收藏、图表和 Excel 统一按 `queryId` 恢复。
- 34 问、57 次执行的双库固定评估集与真实模型执行结果等价评分器。
- 真实浏览器三视口截图、认证态性能脚本、密钥扫描和双库 CI 门槛。
- Java 17 构建/运行基线，以及同时验证 MySQL 与 PostgreSQL 的零密钥 Compose smoke。

### Changed

- SQL Guard 改为 JSqlParser AST 解析失败默认拒绝，并覆盖 CTE、子查询、通配符、锁、文件和危险函数。
- Demo 改为 MySQL / PostgreSQL 等价数据集和独立只读账号。
- 前端改为紧凑数据工作台，增加登录、权限/审计、真实任务阶段和完整状态反馈。
- 登录页拆分为独立懒加载入口，Element Plus 按需加载；哈希静态资源使用长期缓存。
- 修正平板/桌面数据源顶栏收缩和 375px 底部导航溢出，三视口均无需页面横向滚动。
- 图表、数据解读和表格统一读取无损结果数据；图表 JSON 固定输出 `xField` / `yFields` / `seriesField` 并兼容旧快照；修复 Mock 将“各大区已支付订单数量”误判为客户数的意图优先级。
- 宿主机端口统一到 `19030-19039`，Docker 默认使用 `19030-19033`。
- 延长 MySQL 全新卷初始化、Compose `--wait` 和自动 smoke 的就绪预算，慢磁盘下不会在种子导入完成前误判启动失败。
- 文档和性能脚本改为认证态 API，不再把 HTTP 200 或 Mock 输出当准确率/成功证据。
- 密钥扫描覆盖已跟踪文件与未忽略的新增交付文件，未提交工作树也不会漏扫。
- Java / CI / Docker 基线统一为 Java 17，前端构建基线为 Node.js 22。

### Security

- 导出时重新检查当前权限；历史和收藏按用户隔离，快照篡改默认拒绝。
- 高精度结果无损保存；Excel 中和公式样式文本。
- 更新 `brace-expansion` 与 `postcss` 的安全修复版本，前端依赖审计恢复为 0 漏洞。
- 生产模式拒绝默认加密密钥、演示认证、非 Secure Cookie 和通配 CORS。

## [1.0.0] - 2026-07-04

### Added

- **Text2SQL 问数**：自然语言 → LLM 生成 SQL → 只读护栏校验 → 执行 → 自动图表推荐
- **多数据源管理**：支持 MySQL / PostgreSQL，自动读取库表 Schema 与字段注释
- **SQL 安全护栏**：仅允许单条 SELECT；拦截 DML/DDL、多语句与危险函数；自动注入 LIMIT
- **语义层**：为表/字段配置业务别名与描述，提升生成准确率
- **可视化**：表格 + ECharts 柱/折线/饼图智能推荐与手动切换
- **多轮追问**：携带历史上下文继续问数，支持澄清反问
- **查询历史与收藏**：分页浏览、一键重跑、收藏常用问句
- **Excel 导出**：查询结果一键导出
- **可插拔 LLM**：OpenAI 兼容接口，支持 DeepSeek / OpenAI / 通义 / Ollama，Key 走环境变量
- **示例数据**：`sample-data/` 含销售演示库（MySQL / PostgreSQL 双版本）
- **Docker 一键部署**：`docker-compose.yml` 启动 MySQL 示例库 + 后端 + 前端
- **工程化**：Swagger API 文档、统一响应/异常、数据源密码 AES 加密、单元测试（SqlGuard / PromptBuilder）
- **文档**：中文 README、架构说明（`docs/architecture.md`）、使用指南（`docs/USAGE.md`）

### Security

- 目标库连接池强制只读、`maxRows` 与 `queryTimeout` 双限
- JSqlParser AST 解析优先，避免误杀合法列名

[1.0.0]: https://github.com/Hou-mingyuan/chatbi-copilot/releases/tag/v1.0.0
