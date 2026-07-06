#!/usr/bin/env python3
"""ChatBI Copilot HTTP dry-run（health + query/run，不调用 LLM）"""
from __future__ import annotations

import argparse
import json
import statistics
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed


def get(url: str, timeout: float = 10.0) -> float:
    started = time.perf_counter()
    with urllib.request.urlopen(url, timeout=timeout) as resp:
        resp.read()
    return (time.perf_counter() - started) * 1000


def post_json(url: str, payload: dict, timeout: float = 15.0) -> float:
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    started = time.perf_counter()
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        resp.read()
    return (time.perf_counter() - started) * 1000


def one_round(base: str) -> dict[str, float]:
    health_ms = get(f"{base}/health")
    run_ms = post_json(
        f"{base}/query/run",
        {
            "datasourceId": 1,
            "sql": "select category, count(*) as cnt from products group by category limit 10",
        },
    )
    return {"health_ms": health_ms, "run_ms": run_ms}


def pct(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    s = sorted(values)
    return s[int(round((p / 100) * (len(s) - 1))]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080/api")
    parser.add_argument("--iterations", type=int, default=15)
    parser.add_argument("--concurrency", type=int, default=3)
    args = parser.parse_args()
    base = args.base_url.rstrip("/")

    for _ in range(2):
        try:
            one_round(base)
        except Exception:
            pass

    rows: list[dict[str, float]] = []
    errors = 0
    with ThreadPoolExecutor(max_workers=args.concurrency) as ex:
        futs = [ex.submit(one_round, base) for _ in range(args.iterations)]
        for f in as_completed(futs):
            try:
                rows.append(f.result())
            except (urllib.error.URLError, TimeoutError) as e:
                errors += 1
                print(f"ERROR: {e}")

    if not rows:
        print("DRY-RUN FAILED: is backend up?")
        return 1

    h = [r["health_ms"] for r in rows]
    r = [r["run_ms"] for r in rows]
    print(f"health p95={pct(h, 95):.1f}ms  run p95={pct(r, 95):.1f}ms  ok={len(rows)} err={errors}")
    ok = errors == 0 and pct(h, 95) < 300 and pct(r, 95) < 1500
    print("DRY-RUN", "PASSED" if ok else "FAILED")
    return 0 if ok else 2


if __name__ == "__main__":
    raise SystemExit(main())
