# 验收报告说明

| 文件 | 含义 |
| --- | --- |
| `nl2sql-eval-1784511999121.json` | 最新固定集全量真实模型报告：模型题 43/46（93.48%），策略题 11/11，三个门槛全部通过 |
| `nl2sql-eval-glm-5.2.json` | 较早一次固定集全量报告：模型题 46/46，策略题 11/11；用于观察模型非确定性，不作为“最新”结果 |
| `nl2sql-eval-glm-5.2-baseline.json` | 修正文案与评分语义前的真实模型基线，用于解释改动来源 |
| `nl2sql-eval-glm-5.2-focused.json` | 单类问题的聚焦复测，不替代全量报告 |
| `nl2sql-eval-average-rerun.json` | 最新全量失败所涉平均客单价意图的双方言定向复跑：2/2 |
| `nl2sql-eval-regional-rerun.json` | 最新全量失败所涉大区订单意图的双方言定向复跑：2/2 |
| `nl2sql-eval-gpt-5.6-sol-baseline.json` | 无有效官方凭据时的调用失败记录，不是模型准确率 |
| `performance-local.json` | 认证态本地性能首轮 |
| `performance-local-repeat.json` | 相同参数复测 |
| `performance-final-docker.json` | 2026-08-07 全新卷 Java 17 Docker 栈首轮：15/15 业务成功，查询完成 p95 938.45ms，性能门槛失败并保留 |
| `performance-final-docker-rerun.json` | 相同参数稳定复测：15/15，p95 100.87 / 207.75 / 676.99ms，全部预算通过 |
| `performance-final-docker-confirmation.json` | 相同参数确认轮：15/15，p95 74.37 / 103.25 / 468.82ms，全部预算通过 |

真实模型报告与 Mock 回归严格分开。对外当前准确性只引用最新全量报告，并以生成 SQL 与参考 SQL 的真实执行结果等价为评分依据；较早最佳全量和后续定向复跑都不能替代最新全量 `43/46`。
