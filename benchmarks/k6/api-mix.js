import http from 'k6/http';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';
import { benchmarkSummary } from './summary.js';

const baseUrl = __ENV.BASE_URL || 'http://app:8080';
const password = __ENV.BENCHMARK_PASSWORD || 'benchmark-password';
const claimIds = new SharedArray('mixed benchmark claim ids', () =>
  JSON.parse(open(__ENV.CLAIM_IDS_FILE || '/results/claim-ids.json')));

const apiLatency = new Trend('api_latency', true);
const detailLatency = new Trend('api_claim_detail_latency', true);
const myClaimsLatency = new Trend('api_my_claims_latency', true);
const filteredClaimsLatency = new Trend('api_filtered_claims_latency', true);
const reportingLatency = new Trend('api_reporting_latency', true);
const writeLatency = new Trend('api_write_latency', true);
const apiErrors = new Rate('api_errors');
const apiRequests = new Counter('api_requests');

export const options = {
  scenarios: {
    realistic_api_mix: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 20),
      duration: __ENV.DURATION || '60s',
      gracefulStop: '15s',
    },
  },
  summaryTrendStats: ['avg', 'min', 'p(50)', 'p(95)', 'p(99)', 'max', 'count'],
};

function login(username) {
  const response = http.post(`${baseUrl}/api/auth/login`, JSON.stringify({ username, password }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'POST /api/auth/login (setup)' },
  });
  if (response.status !== 200) {
    throw new Error(`${username} login failed with HTTP ${response.status}`);
  }
  return response.json('token');
}

export function setup() {
  if (!Array.isArray(claimIds) || claimIds.length === 0) {
    throw new Error('CLAIM_IDS_FILE must contain a non-empty JSON array');
  }
  const claimantToken = login('bench-claimant');
  const adminToken = login('bench-admin');
  const detail = http.get(`${baseUrl}/api/claims/${claimIds[0]}`, {
    headers: { Authorization: `Bearer ${claimantToken}` },
    tags: { name: 'GET /api/claims/:id (setup)' },
  });
  if (detail.status !== 200) {
    throw new Error(`Claim ownership lookup failed with HTTP ${detail.status}`);
  }
  return {
    claimantToken,
    adminToken,
    claimantId: detail.json('userId'),
  };
}

function record(response, expectedStatus, trend) {
  apiLatency.add(response.timings.duration);
  trend.add(response.timings.duration);
  apiErrors.add(response.status !== expectedStatus);
  apiRequests.add(1);
  return response;
}

function get(url, token, name, trend) {
  return record(http.get(url, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name },
  }), 200, trend);
}

export default function (data) {
  const selector = (exec.vu.idInTest * 37 + exec.scenario.iterationInTest) % 100;
  const claimId = claimIds[(exec.vu.idInTest * 104729 + exec.scenario.iterationInTest * 7919) % claimIds.length];

  if (selector < 45) {
    get(`${baseUrl}/api/claims/${claimId}`, data.claimantToken,
      'GET /api/claims/:id', detailLatency);
    return;
  }
  if (selector < 65) {
    get(`${baseUrl}/api/claims/my?page=${selector % 5}&size=20&sort=claimDate,desc`,
      data.claimantToken, 'GET /api/claims/my', myClaimsLatency);
    return;
  }
  if (selector < 80) {
    get(`${baseUrl}/api/claims?status=SUBMITTED&claimType=MEDICAL&page=${selector % 5}&size=20&sort=claimDate,desc`,
      data.adminToken, 'GET /api/claims (filtered)', filteredClaimsLatency);
    return;
  }
  if (selector < 95) {
    const reportVariant = selector % 4;
    const reportPath = reportVariant === 0 ? '/api/reports/summary'
      : reportVariant === 1 ? '/api/reports/status'
        : reportVariant === 2 ? '/api/reports/claim-types'
          : '/api/reports/daily?from=2025-12-01&to=2025-12-07';
    get(`${baseUrl}${reportPath}`, data.adminToken,
      `GET ${reportPath.split('?')[0]}`, reportingLatency);
    return;
  }

  const operationId = `${__ENV.RUN_LABEL}-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`;
  const createResponse = record(http.post(`${baseUrl}/api/claims`, JSON.stringify({
    userId: data.claimantId,
    claimAmount: `${100 + selector}.50`,
    claimType: ['MEDICAL', 'AUTO', 'HOME', 'TRAVEL', 'LIFE'][selector % 5],
    description: `Synthetic benchmark claim ${operationId}`,
    emailId: 'bench-claimant@benchmark.invalid',
  }), {
    headers: {
      Authorization: `Bearer ${data.claimantToken}`,
      'Content-Type': 'application/json',
      'Idempotency-Key': `benchmark-create-${operationId}`,
    },
    tags: { name: 'POST /api/claims' },
  }), 201, writeLatency);

  if (createResponse.status === 201) {
    const createdClaimId = createResponse.json('claimId');
    record(http.post(`${baseUrl}/api/claims/${createdClaimId}/submit`, null, {
      headers: {
        Authorization: `Bearer ${data.claimantToken}`,
        'Idempotency-Key': `benchmark-submit-${operationId}`,
      },
      tags: { name: 'POST /api/claims/:id/submit' },
    }), 200, writeLatency);
  }
}

export function handleSummary(data) {
  return benchmarkSummary(data);
}
