// Read-only analysis: no dependencies, application changes, or result-file writes.
// Usage: node benchmarks/analysis/summarize-phase16.mjs benchmarks/results/RUN_ID
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const directory = process.argv[2];
assert(directory, 'Supply a benchmark result directory');
const read = name => fs.readFileSync(path.join(directory, name), 'utf8');
const json = name => JSON.parse(read(name));
const median = values => quantile([...values].sort((a, b) => a - b), 0.5);
function quantile(sorted, fraction) {
  const position = (sorted.length - 1) * fraction;
  const lower = Math.floor(position);
  return sorted[lower] + (sorted[Math.ceil(position)] - sorted[lower]) * (position - lower);
}
const improvement = (before, after) => 100 * (before - after) / before;
const increase = (before, after) => 100 * (after - before) / before;
const close = (actual, expected, label) => assert(
  Math.abs(actual - expected) < 1e-7, `${label}: ${actual} != ${expected}`);
function counters(name) {
  return Object.fromEntries([...read(name).matchAll(/^([a-z_]+)[: ]([0-9.]+)\r?$/gm)]
    .map(([, key, value]) => [key, Number(value)]));
}
const csv = new Map(read('api-comparison.csv').trim().split(/\r?\n/).slice(1)
  .map(line => {
    const columns = line.replaceAll('"', '').split(',');
    return [columns[0], columns];
  }));
const labels = [...csv.keys()];
const trials = labels.map(label => {
  const summary = json(`${label}-summary.json`);
  const prefix = label.startsWith('cache-') ? 'claim_detail' : 'api';
  const latency = summary.metrics[`${prefix}_latency`].values;
  const requests = summary.metrics[`${prefix}_requests`].values;
  const errors = summary.metrics[`${prefix}_errors`].values;
  const raw = zlib.gunzipSync(fs.readFileSync(path.join(directory, `${label}-raw.json.gz`)))
    .toString('utf8').trim().split(/\r?\n/).map(line => JSON.parse(line));
  const points = name => raw.filter(row => row.type === 'Point' && row.metric === name);
  const durations = points(`${prefix}_latency`).map(row => row.data.value).sort((a, b) => a - b);
  const rawErrors = points(`${prefix}_errors`);
  assert.equal(durations.length, requests.count, `${label}: raw latency count`);
  assert.equal(rawErrors.length, requests.count, `${label}: raw error count`);
  assert.equal(points(`${prefix}_requests`).reduce((n, row) => n + row.data.value, 0), requests.count);
  const failures = rawErrors.reduce((n, row) => n + row.data.value, 0);
  close(failures / rawErrors.length, errors.rate, `${label}: error rate`);
  for (const percentile of [50, 95, 99]) {
    close(quantile(durations, percentile / 100), latency[`p(${percentile})`], `${label}: p${percentile}`);
  }
  const columns = csv.get(label);
  assert.equal(columns[1], summary.configuration.authenticationMode, `${label}: authentication mode`);
  [latency['p(50)'], latency['p(95)'], latency['p(99)'], requests.rate, errors.rate, requests.count]
    .forEach((value, i) => close(value, Number(columns[i + 2]), `${label}: CSV column ${i + 2}`));
  const redis = counters(`${label}-redis-stats.txt`);
  const before = counters(`${label}-before-app-cpu-stat.txt`);
  const after = counters(`${label}-after-app-cpu-stat.txt`);
  const cpuDelta = Object.fromEntries(Object.keys(before).map(key => [key, after[key] - before[key]]));
  const postgresValues = read(`${label}-postgres-stats.txt`).split(/\r?\n/)
    .find(line => line.includes('claims_benchmark')).split('|').map(part => part.trim());
  const endpoints = {};
  for (const row of points('http_req_duration')) {
    if (row.data.tags.group === '::setup') continue;
    const name = row.data.tags.name;
    const endpoint = endpoints[name] ??= { count: 0, statuses: {}, durations: [] };
    endpoint.count++;
    endpoint.statuses[row.data.tags.status] = (endpoint.statuses[row.data.tags.status] ?? 0) + 1;
    endpoint.durations.push(row.data.value);
  }
  for (const endpoint of Object.values(endpoints)) {
    endpoint.durations.sort((a, b) => a - b);
    for (const p of [50, 95, 99]) endpoint[`p${p}Ms`] = quantile(endpoint.durations, p / 100);
    delete endpoint.durations;
  }
  assert.equal(Object.values(endpoints).reduce((n, endpoint) => n + endpoint.count, 0), requests.count,
    `${label}: measured HTTP endpoint counts`);
  const getTiming = read(`${label}-redis-stats.txt`).match(/^cmdstat_get:.*usec_per_call=([0-9.]+)/m);
  return {
    label, authenticationMode: summary.configuration.authenticationMode,
    p50Ms: latency['p(50)'], p95Ms: latency['p(95)'], p99Ms: latency['p(99)'],
    requestsPerSecond: requests.rate, requests: requests.count, failures, errorRate: errors.rate,
    redisHits: redis.keyspace_hits, redisMisses: redis.keyspace_misses,
    redisHitRatePercent: redis.keyspace_hits + redis.keyspace_misses > 0
      ? 100 * redis.keyspace_hits / (redis.keyspace_hits + redis.keyspace_misses) : null,
    redisKeysAtEnd: Number(read(`${label}-redis-stats.txt`).trim().split(/\r?\n/).at(-1)),
    redisGetMicrosecondsPerCall: getTiming ? Number(getTiming[1]) : null,
    redisUsedMemoryBytes: redis.used_memory, redisEvictions: redis.evicted_keys,
    redisExpirations: redis.expired_keys, redisErrors: redis.total_error_replies,
    redisRejectedConnections: redis.rejected_connections,
    postgresCommits: Number(postgresValues[1]), postgresBlocksRead: Number(postgresValues[2]),
    postgresBlocksHit: Number(postgresValues[3]), postgresTuplesFetched: Number(postgresValues[5]),
    cpuDelta, cpuThrottledPeriodsPercent: 100 * cpuDelta.nr_throttled / cpuDelta.nr_periods,
    endpoints,
  };
});
const cacheOff = trials.filter(t => t.label.startsWith('cache-off-'));
const cacheOn = trials.filter(t => t.label.startsWith('cache-on-'));
const metrics = ['p50Ms', 'p95Ms', 'p99Ms', 'requestsPerSecond'];
const comparison = Object.fromEntries(metrics.map(metric => {
  const off = median(cacheOff.map(t => t[metric]));
  const on = median(cacheOn.map(t => t[metric]));
  return [metric, { cacheOffMedian: off, cacheOnMedian: on,
    improvementPercent: metric === 'requestsPerSecond' ? increase(off, on) : improvement(off, on) }];
}));
const paired = cacheOff.map(off => {
  const on = cacheOn.find(t => t.label.endsWith(off.label.split('-').at(-1)));
  return { repetition: off.label.split('-').at(-1), ...Object.fromEntries(metrics.map(metric => [metric,
    metric === 'requestsPerSecond' ? increase(off[metric], on[metric]) : improvement(off[metric], on[metric])])) };
});
const sum = (items, key) => items.reduce((n, item) => n + item[key], 0);
const aggregateCacheOnHits = sum(cacheOn, 'redisHits');
const aggregateCacheOnMisses = sum(cacheOn, 'redisMisses');
const indexTrials = ['without-index', 'with-index'].map(label => {
  const text = read(`${label}-pgbench.txt`);
  const explain = json(`${label}-explain.json`)[0];
  const scan = explain.Plan.Plans[0].Plans[0];
  return { label,
    averageLatencyMs: Number(text.match(/latency average = ([0-9.]+)/)[1]),
    transactionsPerSecond: Number(text.match(/tps = ([0-9.]+)/)[1]),
    transactions: Number(text.match(/actually processed: (\d+)/)[1]),
    failedTransactions: Number(text.match(/failed transactions: (\d+)/)[1]),
    executionTimeMs: explain['Execution Time'], planningTimeMs: explain['Planning Time'],
    totalSharedBlocks: explain.Plan['Shared Hit Blocks'] + explain.Plan['Shared Read Blocks'],
    sharedHitBlocks: explain.Plan['Shared Hit Blocks'], sharedReadBlocks: explain.Plan['Shared Read Blocks'],
    scan: scan['Node Type'], matchedRows: scan['Actual Rows'],
    rowsRemovedByFilter: scan['Rows Removed by Filter'] ?? 0,
  };
});
const [withoutIndex, withIndex] = indexTrials;
console.log(JSON.stringify({
  verification: `All ${trials.length} measured gzip streams agree with summaries and CSV: custom request counts, errors, p50/p95/p99. Warmups/setup excluded.`,
  trials, comparison, pairedImprovementPercent: paired,
  totals: { measuredApiRequests: sum(trials, 'requests'), measuredFailures: sum(trials, 'failures'),
    cacheOffRequests: sum(cacheOff, 'requests'), cacheOnRequests: sum(cacheOn, 'requests'),
    aggregateCacheOnHits, aggregateCacheOnMisses,
    aggregateCacheOnHitRatePercent: 100 * aggregateCacheOnHits / (aggregateCacheOnHits + aggregateCacheOnMisses),
    postgresCacheOffCommits: sum(cacheOff, 'postgresCommits'), postgresCacheOnCommits: sum(cacheOn, 'postgresCommits'),
    postgresCommitsPerRequestReductionPercent: improvement(sum(cacheOff, 'postgresCommits') / sum(cacheOff, 'requests'),
      sum(cacheOn, 'postgresCommits') / sum(cacheOn, 'requests')) },
  indexTrials, indexComparison: {
    averageLatencyReductionPercent: improvement(withoutIndex.averageLatencyMs, withIndex.averageLatencyMs),
    throughputIncreasePercent: increase(withoutIndex.transactionsPerSecond, withIndex.transactionsPerSecond),
    throughputRatio: withIndex.transactionsPerSecond / withoutIndex.transactionsPerSecond,
    explainExecutionReductionPercent: improvement(withoutIndex.executionTimeMs, withIndex.executionTimeMs),
    explainSharedBlockReductionPercent: improvement(withoutIndex.totalSharedBlocks, withIndex.totalSharedBlocks),
  },
}, null, 2));
