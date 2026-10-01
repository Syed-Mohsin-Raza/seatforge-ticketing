import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const cacheMisses = new Counter('cache_misses');
const cacheHits = new Counter('cache_hits');

export const options = {
  stages: [
    { duration: '10s', target: 500 },
    { duration: '30s', target: 500 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<200', 'p(99)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const EVENT_ID = __ENV.EVENT_ID;

export default function () {
  const res = http.get(`http://localhost:8080/api/v1/events/${EVENT_ID}/seats`);

  check(res, { 'status 200': (r) => r.status === 200 });

  const cacheHeader = res.headers['X-Cache'];
  if (cacheHeader === 'MISS') cacheMisses.add(1);
  else if (cacheHeader === 'HIT') cacheHits.add(1);
}