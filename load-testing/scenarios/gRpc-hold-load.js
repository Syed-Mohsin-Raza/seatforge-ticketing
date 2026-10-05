import http from "k6/http";
import { check } from "k6";
import { Counter, Trend } from "k6/metrics";
import { randomIntBetween } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

// Separate metrics for the two scenarios
const holdSuccess = new Counter("hold_success");
const holdConflict = new Counter("hold_conflict");
const holdError = new Counter("hold_error");
const holdLatency = new Trend("hold_latency", true);

const summaryHit = new Counter("summary_hit");
const summaryMiss = new Counter("summary_miss");
const summaryLatency = new Trend("summary_latency", true);

export const options = {
  scenarios: {
    read_summary_cached: {
      executor: "constant-vus",
      vus: 500,
      duration: "30s",
      exec: "readSummary",
      startTime: "0s",
      tags: { scenario: "read" },
    },
    hold_no_contention: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "20s", target: 200 },
        { duration: "20s", target: 500 },
        { duration: "20s", target: 700 },
        { duration: "10s", target: 700 },
        { duration: "10s", target: 0 },
      ],
      exec: "holdDistinctSeat",
      startTime: "45s", // 15 s gap after read finishes
      tags: { scenario: "hold" },
    },
  },
  thresholds: {
    hold_latency: ["p(95)<500"],
    summary_latency: ["p(95)<100"],
    hold_error: ["count<50"],
  },
};

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const EVENT_ID = __ENV.EVENT_ID || "1";

export function readSummary() {
  const res = http.get(`${BASE_URL}/api/v1/events/${EVENT_ID}/seats/summary`);
  check(res, { "summary status 200": (r) => r.status === 200 });

  const cacheHeader = res.headers["X-Cache"];
  if (cacheHeader === "MISS") summaryMiss.add(1);
  else if (cacheHeader === "HIT") summaryHit.add(1);

  summaryLatency.add(res.timings.duration);
}

export function holdDistinctSeat() {
  // Private seat range per VU. 100,000 seats / 1000 VU slots = 100 seats per slot.
  const base = (((__VU - 1) * 100) % 99000) + 1;
  const seatId = base + (__ITER % 100);
  const userId = `k6-${__VU}-${__ITER}`;

  const res = http.post(
    `${BASE_URL}/api/v1/bookings/holds`,
    JSON.stringify({ seatId, userId }),
    { headers: { "Content-Type": "application/json" } },
  );

  if (res.status === 201) holdSuccess.add(1);
  else if (res.status === 409) holdConflict.add(1);
  else holdError.add(1);

  check(res, { "hold responded": (r) => r.status === 201 || r.status === 409 });
  holdLatency.add(res.timings.duration);
}

export function handleSummary(data) {
  return {
    "load-testing/results/gRpc-hold-load.json": JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: " ", enableColors: true }),
  };
}

import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";
