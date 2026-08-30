import http from "k6/http";
import { sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";
import { SharedArray } from "k6/data";
import exec from "k6/execution";
import { benchmarkSummary } from "./summary.js";

const baseUrl = __ENV.BASE_URL || "http://app:8080";
const username = __ENV.BENCHMARK_USERNAME || "bench-admin";
const password = __ENV.BENCHMARK_PASSWORD || "benchmark-password";
const claimIds = new SharedArray("benchmark claim ids", () => {
  const values = JSON.parse(
    open(__ENV.CLAIM_IDS_FILE || "/results/claim-ids.json"),
  );
  if (!Array.isArray(values) || values.length === 0) {
    throw new Error("CLAIM_IDS_FILE must contain a non-empty JSON array");
  }
  return values;
});

const detailLatency = new Trend("claim_detail_latency", true);
const detailErrors = new Rate("claim_detail_errors");
const detailRequests = new Counter("claim_detail_requests");

export const options = {
  scenarios: {
    claim_detail_reads: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 20),
      duration: __ENV.DURATION || "60s",
      gracefulStop: "10s",
    },
  },
  summaryTrendStats: ["avg", "min", "p(50)", "p(95)", "p(99)", "max", "count"],
  discardResponseBodies: true,
};

export function setup() {
  const response = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({ username, password }),
    {
      headers: { "Content-Type": "application/json" },
      tags: { name: "POST /api/auth/login (setup)" },
      responseType: "text",
    },
  );
  if (response.status !== 200) {
    throw new Error(`Benchmark login failed with HTTP ${response.status}`);
  }
  return { token: response.json("token") };
}

export default function (data) {
  const configuredHotset = Number(__ENV.HOTSET_PERCENT || 10);
  const hotsetSize = Math.max(
    1,
    Math.floor((claimIds.length * configuredHotset) / 100),
  );
  const selector =
    exec.vu.idInTest * 104729 + exec.scenario.iterationInTest * 7919;
  const useHotset = selector % 10 < 8;
  const index = useHotset ? selector % hotsetSize : selector % claimIds.length;
  const claimId = claimIds[index];

  const response = http.get(`${baseUrl}/api/claims/${claimId}`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { name: "GET /api/claims/:id" },
  });
  const failed = response.status !== 200;
  detailLatency.add(response.timings.duration);
  detailErrors.add(failed);
  detailRequests.add(1);

  const pauseMs = Number(__ENV.PAUSE_MS || 0);
  if (pauseMs > 0) {
    sleep(pauseMs / 1000);
  }
}

export function handleSummary(data) {
  return benchmarkSummary(data);
}
