# ChatBI Copilot 性能方法

## 预算

本地核心链路预算不包含外部 LLM：

| 指标 | p95 预算 |
| --- | ---: |
| 认证态普通读接口 | `≤ 300 ms` |
| 查询任务创建（H2 写入） | `≤ 800 ms` |
| 任务创建至本地只读 SQL 完成 | `≤ 800 ms` |

真实 LLM 延迟受供应商、模型、配额和网络影响，在固定问数报告中逐题记录，不混入本地 API 预算。

## 本地门槛

`loadtest/dry_run.py` 会：

1. 为每个并发客户端登录并持有独立 Cookie / CSRF；
2. 从授权数据源列表选择已核验的 MySQL；
3. 预热每个客户端；
4. 测量 `/auth/me`、`POST /query/jobs/run` 和任务完成时间；
5. 轮询真实任务状态；
6. 对每次结果断言已支付订单为 `20`、金额为 `185551.00`；
7. 任一 HTTP/业务错误、错数或 p95 超预算均返回非零退出码。

```bash
python loadtest/dry_run.py \
  --base-url http://127.0.0.1:19030/api \
  --iterations 15 \
  --concurrency 3 \
  --output reports/performance-local.json
```

可通过 `CHATBI_PERF_USERNAME` / `CHATBI_PERF_PASSWORD` 覆盖账号。

## k6 并发脚本

`performance/k6-smoke.js` 使用一次认证会话并发执行：

- `GET /auth/me`，预算 p95 `< 300 ms`；
- `POST /query/run`，预算 p95 `< 800 ms`；
- 对响应 envelope 和固定聚合数字做业务检查。

本机安装 k6：

```bash
k6 run performance/k6-smoke.js \
  -e BASE_URL=http://127.0.0.1:19030/api \
  -e VUS=10 \
  -e DURATION=30s
```

Docker 运行 k6 时，backend 仍只能映射到 `19030-19039`；可使用 `http://host.docker.internal:19030/api`。

## 最新实测

2026-07-20，Windows 本地源码 backend、MySQL `19032`、PostgreSQL `19033`，每轮 15 次、并发 3、每客户端先预热：

| 轮次 | 成功/总数 | 读 p95 | 创建 p95 | 完成 p95 | 结果 |
| --- | ---: | ---: | ---: | ---: | --- |
| 首轮 | 15/15 | 46.70 ms | 35.38 ms | 553.99 ms | 通过 |
| 复测 | 15/15 | 37.96 ms | 37.56 ms | 344.46 ms | 通过 |

原始 JSON：`reports/performance-local.json`、`reports/performance-local-repeat.json`。完整结论见 [PERFORMANCE_REPORT.md](../PERFORMANCE_REPORT.md)。

2026-08-07，当前源码在全新卷 Java 17 Compose 栈（backend `19034`、MySQL `19036`）复验：

| 轮次 | 成功/总数 | 读 p95 | 创建 p95 | 完成 p95 | 结果 |
| --- | ---: | ---: | ---: | ---: | --- |
| 首轮 | 15/15 | 109.25 ms | 247.67 ms | 938.45 ms | 失败；原始结果保留 |
| 稳定复测 | 15/15 | 100.87 ms | 207.75 ms | 676.99 ms | 通过 |
| 确认轮 | 15/15 | 74.37 ms | 103.25 ms | 468.82 ms | 通过 |

完成门槛以相同参数连续两轮通过为准，不能删除首轮不合格结果。原始 JSON 见 `reports/performance-final-docker*.json`。

## 解释与限制

- 这是小并发本地门槛，用来发现认证、H2 写入、任务调度、轮询或只读 SQL 的明显回归，不是生产容量承诺。
- 任务完成时间包含队列、SQL Guard、EXPLAIN、目标库查询、快照和历史写入。
- 登录 BCrypt 成本不计入上述 p95；登录限速和密码校验由安全集成测试覆盖。
- 生产容量测试应使用脱敏的代表性 Schema、数据量、查询分布和独立负载机。
- 不能只检查 HTTP 200；所有脚本都必须检查 envelope、任务终态和固定结果数字。
