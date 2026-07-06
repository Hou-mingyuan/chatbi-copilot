/**
 * ChatBI smoke: GET /api/health + POST /api/query/run (no LLM)
 * k6 run loadtest/k6_smoke.js -e BASE_URL=http://localhost:8080/api
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = (__ENV.BASE_URL || 'http://localhost:8080/api').replace(/\/$/, '');

export const options = {
  vus: 3,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.02'],
    'http_req_duration{endpoint:health}': ['p(95)<300'],
    'http_req_duration{endpoint:run}': ['p(95)<1500'],
  },
};

export default function () {
  const health = http.get(`${BASE}/health`, { tags: { endpoint: 'health' } });
  check(health, { 'health ok': (r) => r.status === 200 });

  const payload = JSON.stringify({
    datasourceId: 1,
    sql: 'select region, count(*) as cnt from customers group by region limit 20',
  });
  const run = http.post(`${BASE}/query/run`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { endpoint: 'run' },
  });
  check(run, { 'run ok': (r) => r.status === 200 });

  sleep(0.3);
}
