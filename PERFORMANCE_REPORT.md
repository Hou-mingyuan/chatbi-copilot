# ChatBI Copilot 性能报告

报告日期：2026-07-06

## 目标

生产化评估目标（**不含 LLM 问数长链路**）：

- 50 并发用户（只读 API smoke）。
- P95 响应时间 `< 800ms`，针对 `/api/health`、`/api/datasources`、`/api/query/run`。
- 错误率 `< 1%`。

`/api/query/ask` 依赖外部 LLM，延迟与供应商配额相关，单独统计，不纳入上述 P95 门槛。

## 压测脚本

脚本：`performance/k6-smoke.js`

前置：Docker Compose 已启动且 Demo 数据源可用（默认 `datasourceId=1`）。

```bash
docker run --rm ^
  -e BASE_URL=http://host.docker.internal:8080 ^
  -e VUS=50 ^
  -e DURATION=1m ^
  -e THINK_TIME_SECONDS=1 ^
  -v D:/project-hub/chatbi-copilot/performance:/scripts ^
  grafana/k6:latest run /scripts/k6-smoke.js
```

自定义端口示例：

```bash
-e BASE_URL=http://host.docker.internal:18082
```

## 当前状态

已完成：

- 后端单元测试覆盖 SqlGuard（放行/拦截/LIMIT/CTE/多语句）与 PromptBuilder。
- Docker Compose 三件套（MySQL + backend + frontend）可一键启动。
- 已提供 k6 smoke 脚本，覆盖无 LLM 依赖的只读路径。
- 目标库连接池：`readOnly=true`，单数据源最大 5 连接；SqlGuard 强制 LIMIT 与 query timeout。

待生产化强化（见 README Roadmap）：

- 应用层认证与 API 限流
- 连接池与线程池可配置化
- 针对 `/api/query/ask` 的独立 LLM 超时与并发隔离

## 已检查的性能设计点

| 项 | 实现 |
| --- | --- |
| SQL 结果集上限 | SqlGuard `default-limit=500`，`max-limit=5000` |
| 目标库连接 | HikariCP，`maximumPoolSize=5`，`readOnly=true` |
| LLM 超时 | `LLM_TIMEOUT_SECONDS`（默认 60s） |
| 前端代理 | Nginx `proxy_read_timeout 300s` |
| 元数据存储 | 内嵌 H2 文件库，零外部依赖启动 |

## 建议压测步骤

1. `docker compose up -d --build`，确认 `curl /api/health` 返回 UP。
2. 运行 k6 smoke（10 VUs → 50 VUs 递增）。
3. 观察 `docker compose logs backend` 是否有连接池耗尽或 SQL 超时。
4. 若 P95 超标，优先检查：宿主机 CPU/内存、MySQL 健康、同机其他服务争用端口。

## 相关文档

- [部署指南](DEPLOYMENT.md)
- [安全策略](SECURITY.md)
- [使用指南](docs/USAGE.md)
