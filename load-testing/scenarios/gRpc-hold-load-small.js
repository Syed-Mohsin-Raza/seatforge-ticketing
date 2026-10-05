// load-testing/scenarios/gRpc-hold-load-small.js
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const holdSuccess = new Counter('hold_success');
const holdConflict = new Counter('hold_conflict');
const holdError = new Counter('hold_error');
const holdLatency = new Trend('hold_latency', true);

export const options = {
  scenarios: {
    hold_only: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 100 },
        { duration: '30s', target: 100 },
        { duration: '10s', target: 0 },
      ],
      exec: 'holdDistinctSeat',
    },
  },
  thresholds: {
    'hold_latency': ['p(95)<200'],
    'hold_error': ['count<5'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function holdDistinctSeat() {
  const base = ((__VU - 1) * 100) % 99000 + 1;
  const seatId = base + (__ITER % 100);
  const userId = `k6-${__VU}-${__ITER}`;

  const res = http.post(
    `${BASE_URL}/api/v1/bookings/holds`,
    JSON.stringify({ seatId, userId }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  if (res.status === 201) holdSuccess.add(1);
  else if (res.status === 409) holdConflict.add(1);
  else holdError.add(1);

  check(res, { 'hold responded': (r) => r.status === 201 || r.status === 409 });
  holdLatency.add(res.timings.duration);
}