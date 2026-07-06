# ChatBI Copilot · 性能与压测基线

> 本页描述 **不调用 LLM** 的 HTTP smoke / 轻量压测，用于验证部署与健康探活。自然语言 `/api/query/ask` 依赖外部 LLM，**不在默认可重复压测范围内**。

## 目标端点

| 端点 | 方法 | 用途 |
| --- | --- | --- |
| `/api/health` | GET | 探活、Compose smoke |
| `/api/datasources` | GET | 确认 Demo 数据源已注册 |
| `/api/query/run` | POST | 护栏 + 只读 SQL 执行（无 LLM） |

## 预期延迟（本地 Docker，参考值）

| 指标 | p95 目标 | 说明 |
| --- | --- | --- |
| `/api/health` | < 100 ms | 无 DB 依赖或极轻 |
| `/api/query/run`（聚合 SQL） | < 800 ms | Demo 库小表 |
| `/api/query/ask` | 3–30 s | **视 LLM**，不做 CI 压测 |

## 手动 smoke

```bash
BASE=http://localhost:8080/api

curl -sf "$BASE/health"
curl -sf "$BASE/datasources"
curl -sf -X POST "$BASE/query/run" \
  -H "Content-Type: application/json" \
  -d '{"datasourceId":1,"sql":"select region, count(*) as cnt from customers group by region limit 20"}'
```

## Python dry-run（并发）

需后端已启动：

```bash
python loadtest/dry_run.py --base-url http://localhost:8080/api
```

脚本对 `health` + `query/run` 做预热与 p95 统计；成功输出 `DRY-RUN PASSED`。

## k6（可选）

```bash
k6 run loadtest/k6_smoke.js -e BASE_URL=http://localhost:8080/api
```

## CI 建议

1. `mvn test` — SqlGuard 单元测试（必跑）
2. `docker compose up -d --build` + `curl /api/health` — 集成 smoke
3. 可选：`python loadtest/dry_run.py` — 回归延迟基线

## 限制

- 未覆盖 LLM 供应商 SLA、复杂 Text2SQL 准确率。
- 生产压测前使用独立只读库与配额监控，避免影响业务库。
