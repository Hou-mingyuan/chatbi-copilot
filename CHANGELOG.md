# Changelog

本项目的所有重要变更均记录在此文件。格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

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
