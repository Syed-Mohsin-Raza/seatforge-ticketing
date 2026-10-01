import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const cacheMisses = new Counter('cache_misses');
const cacheHits = new Counter('cache_hits');
const serverLatencyHit = new Trend('server_latency_hit', true);
const serverLatencyMiss = new Trend('server_latency_miss', true);

export const options = {
  scenarios: {
    stampede: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5s',  target: 500 },
        { duration: '20s', target: 500 },
        { duration: '5s',  target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<200'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID || '1';

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/events/${EVENT_ID}/seats/summary`);

  check(res, { 'status is 200': (r) => r.status === 200 });

  const cacheHeader = res.headers['X-Cache'];
  if (cacheHeader === 'MISS') {
    cacheMisses.add(1);
    serverLatencyMiss.add(res.timings.duration);
  } else if (cacheHeader === 'HIT') {
    cacheHits.add(1);
    serverLatencyHit.add(res.timings.duration);
  }
}

export function handleSummary(data) {
  return {
    'load-testing/results/before-summary.json': JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}

import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';