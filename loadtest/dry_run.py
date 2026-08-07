#!/usr/bin/env python3
"""Authenticated local performance gate; does not call an LLM."""
from __future__ import annotations

import argparse
import http.cookiejar
import json
import os
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Any


TERMINAL = {"SUCCEEDED", "PREVIEWED", "CLARIFICATION", "NEEDS_CONFIRMATION", "FAILED", "CANCELLED", "TIMED_OUT"}


class ApiClient:
    def __init__(self, base: str, username: str, password: str) -> None:
        self.base = base.rstrip("/")
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))
        login, _ = self._request("POST", "/auth/login", {"username": username, "password": password}, csrf=False)
        self.csrf = str(login["csrfToken"])
        datasources, _ = self._request("GET", "/datasources")
        mysql = next((item for item in datasources if item.get("dbType") == "mysql"), None)
        if not mysql or not mysql.get("verifiedReadOnly"):
            raise RuntimeError("verified MySQL datasource not found")
        self.datasource_id = int(mysql["id"])

    def _request(self, method: str, route: str, payload: dict[str, Any] | None = None,
                 *, csrf: bool = True) -> tuple[Any, float]:
        data = json.dumps(payload).encode("utf-8") if payload is not None else None
        headers = {"Accept": "application/json"}
        if data is not None:
            headers["Content-Type"] = "application/json"
        if csrf and method not in {"GET", "HEAD"}:
            headers["X-CSRF-Token"] = self.csrf
        request = urllib.request.Request(f"{self.base}{route}", data=data, headers=headers, method=method)
        started = time.perf_counter()
        try:
            with self.opener.open(request, timeout=15) as response:
                raw = response.read()
        except urllib.error.HTTPError as error:
            raw = error.read()
            raise RuntimeError(f"HTTP {error.code} {route}: {raw[:240]!r}") from error
        elapsed = (time.perf_counter() - started) * 1000
        envelope = json.loads(raw)
        if envelope.get("code") != 0:
            raise RuntimeError(f"{route}: {envelope.get('message')} ({envelope.get('requestId')})")
        return envelope.get("data"), elapsed

    def round(self) -> dict[str, float]:
        _, read_ms = self._request("GET", "/auth/me")
        started = time.perf_counter()
        job, create_ms = self._request("POST", "/query/jobs/run", {
            "datasourceId": self.datasource_id,
            "sql": "SELECT COUNT(*) AS paid_order_count, SUM(total_amount) AS paid_order_amount FROM orders WHERE status='paid'"
        })
        deadline = time.monotonic() + 10
        while job.get("status") not in TERMINAL:
            if time.monotonic() > deadline:
                raise TimeoutError(f"query job {job.get('id')} exceeded 10 seconds")
            time.sleep(0.02)
            job, _ = self._request("GET", f"/query/jobs/{job['id']}")
        complete_ms = (time.perf_counter() - started) * 1000
        if job.get("status") != "SUCCEEDED":
            raise RuntimeError(f"query job ended in {job.get('status')}: {job.get('errorMessage')}")
        row = (job.get("result") or {}).get("rows", [{}])[0]
        values = {float(value) for value in row.values() if isinstance(value, (int, float, str)) and _number(value) is not None}
        if 20.0 not in values or 185551.0 not in values:
            raise RuntimeError(f"unexpected aggregate: {row}")
        return {"read_ms": read_ms, "create_ms": create_ms, "complete_ms": complete_ms}

    def close(self) -> None:
        self._request("POST", "/auth/logout")


def _number(value: Any) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def percentile(values: list[float], p: float) -> float:
    ordered = sorted(values)
    return ordered[int(round((p / 100) * (len(ordered) - 1)))] if ordered else 0.0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:19030/api")
    parser.add_argument("--iterations", type=int, default=15)
    parser.add_argument("--concurrency", type=int, default=3)
    parser.add_argument("--output", default="")
    args = parser.parse_args()
    if args.iterations < 1 or args.concurrency < 1:
        raise SystemExit("iterations and concurrency must be positive")

    username = os.getenv("CHATBI_PERF_USERNAME", "admin")
    password = os.getenv("CHATBI_PERF_PASSWORD", os.getenv("DEMO_ADMIN_PASSWORD", "ChatBI!Admin123"))
    clients = [ApiClient(args.base_url, username, password) for _ in range(min(args.concurrency, args.iterations))]
    for client in clients:
        client.round()

    rows: list[dict[str, float]] = []
    errors: list[str] = []
    remaining = args.iterations
    with ThreadPoolExecutor(max_workers=len(clients)) as executor:
        while remaining:
            batch = min(remaining, len(clients))
            futures = [executor.submit(clients[index].round) for index in range(batch)]
            for future in futures:
                try:
                    rows.append(future.result())
                except Exception as error:  # noqa: BLE001 - report every failed request as gate evidence
                    errors.append(str(error))
            remaining -= batch

    for client in clients:
        try:
            client.close()
        except Exception as error:  # noqa: BLE001
            errors.append(f"logout: {error}")

    report = {
        "baseUrl": args.base_url,
        "iterations": args.iterations,
        "concurrency": len(clients),
        "successful": len(rows),
        "errors": errors,
        "p95Ms": {
            "authenticatedRead": round(percentile([row["read_ms"] for row in rows], 95), 2),
            "queryJobCreate": round(percentile([row["create_ms"] for row in rows], 95), 2),
            "queryComplete": round(percentile([row["complete_ms"] for row in rows], 95), 2),
        },
        "budgetsMs": {"authenticatedRead": 300, "queryJobCreate": 800, "queryComplete": 800},
    }
    report["passed"] = (
        not errors
        and len(rows) == args.iterations
        and report["p95Ms"]["authenticatedRead"] <= 300
        and report["p95Ms"]["queryJobCreate"] <= 800
        and report["p95Ms"]["queryComplete"] <= 800
    )
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    print(rendered)
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(rendered + "\n", encoding="utf-8")
    return 0 if report["passed"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
