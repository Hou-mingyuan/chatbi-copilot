import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  scenarios: {
    smoke: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || "1m",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{type:fast}": ["p(95)<800"],
  },
};

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8080";
const DATASOURCE_ID = Number(__ENV.DATASOURCE_ID || 1);
const THINK_TIME_SECONDS = Number(__ENV.THINK_TIME_SECONDS || 1);

const RUN_SQL_PAYLOAD = JSON.stringify({
  datasourceId: DATASOURCE_ID,
  sql:
    "select category, sum(amount) as total_amount from sales_orders group by category order by total_amount desc limit 10",
});

export default function () {
  const health = http.get(`${BASE_URL}/api/health`, { tags: { type: "fast" } });
  check(health, { "health ok": (r) => r.status === 200 });

  const datasources = http.get(`${BASE_URL}/api/datasources`, { tags: { type: "fast" } });
  check(datasources, { "datasources ok": (r) => r.status === 200 });

  const run = http.post(`${BASE_URL}/api/query/run`, RUN_SQL_PAYLOAD, {
    headers: { "Content-Type": "application/json" },
    tags: { type: "fast" },
  });
  check(run, { "query run ok": (r) => r.status === 200 });

  sleep(THINK_TIME_SECONDS);
}
