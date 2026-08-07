# ChatBI Copilot 性能报告

报告日期：2026-08-07

## 结论

认证态本地性能门槛连续两轮通过。当前源码的全新卷 Docker 栈首个测量轮次中，查询完成 p95 为 `938.45 ms`，超过 `800 ms` 预算；该失败结果保留。随后相同参数连续两轮分别降至 `676.99 ms` 和 `468.82 ms`，全部预算通过。三轮共 45 次查询均无 HTTP/业务错误、任务失败或结果错数。

| 指标 | 预算 | 7 月本地首轮 | 7 月本地复测 | 8 月 Docker 首轮 | 8 月稳定复测 | 8 月确认轮 | 判断 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `/auth/me` | ≤ 300 ms | 46.70 ms | 37.96 ms | 109.25 ms | 100.87 ms | 74.37 ms | 通过 |
| `POST /query/jobs/run` 创建 | ≤ 800 ms | 35.38 ms | 37.56 ms | 247.67 ms | 207.75 ms | 103.25 ms | 通过 |
| 创建至查询成功 | ≤ 800 ms | 553.99 ms | 344.46 ms | **938.45 ms** | 676.99 ms | 468.82 ms | 连续两轮通过；首轮失败保留 |

每轮 15 次、并发 3，每个客户端预热一次。所有查询实际连接 MySQL 只读账号，经过 SQL Guard、ACL、EXPLAIN、执行、快照和历史写入，并断言返回 `20` 单、`185551.00` 元。当前门槛以连续两轮稳定通过为准，不用单次绿色结果覆盖首轮失败。

## 环境

- Windows 本地源码 backend：`127.0.0.1:19030`
- MySQL 8.0：`127.0.0.1:19032`
- PostgreSQL 16：`127.0.0.1:19033`
- 元数据库：H2 文件模式
- LLM：不参与本性能测试
- 最终 Docker 复测：backend `19034`、frontend `19035`、MySQL `19036`、PostgreSQL `19037`，使用全新 Compose 卷和 Java 17 运行镜像

## 命令

```bash
python loadtest/dry_run.py \
  --base-url http://127.0.0.1:19030/api \
  --iterations 15 --concurrency 3 \
  --output reports/performance-local.json

python loadtest/dry_run.py \
  --base-url http://127.0.0.1:19030/api \
  --iterations 15 --concurrency 3 \
  --output reports/performance-local-repeat.json

python loadtest/dry_run.py \
  --base-url http://127.0.0.1:19034/api \
  --iterations 15 --concurrency 3 \
  --output reports/performance-final-docker.json

python loadtest/dry_run.py \
  --base-url http://127.0.0.1:19034/api \
  --iterations 15 --concurrency 3 \
  --output reports/performance-final-docker-rerun.json

python loadtest/dry_run.py \
  --base-url http://127.0.0.1:19034/api \
  --iterations 15 --concurrency 3 \
  --output reports/performance-final-docker-confirmation.json
```

## 原始报告

- `reports/performance-local.json`
- `reports/performance-local-repeat.json`
- `reports/performance-final-docker.json`（当前全新栈首轮，保留失败）
- `reports/performance-final-docker-rerun.json`（稳定复测通过）
- `reports/performance-final-docker-confirmation.json`（确认轮通过）

## 边界

该结果是开发机回归门槛，不代表生产容量或外部 LLM SLA。真实 LLM 的逐题延迟记录在固定评估报告中；生产压测必须使用代表性数据量、独立负载机和目标并发重新执行。
