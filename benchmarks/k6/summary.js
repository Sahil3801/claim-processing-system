function selectedMetrics(data) {
  const selected = {};
  for (const [name, metric] of Object.entries(data.metrics || {})) {
    if (name.startsWith('claim_') || name.startsWith('api_') ||
        ['http_req_duration', 'http_req_failed', 'http_reqs', 'iterations'].includes(name)) {
      selected[name] = metric.values;
    }
  }
  return selected;
}

export function benchmarkSummary(data) {
  const label = __ENV.RUN_LABEL || 'unlabelled';
  const outputDirectory = __ENV.RESULT_DIR || '/results';
  const report = {
    label,
    generatedAt: new Date().toISOString(),
    metricUnits: {
      latency: 'milliseconds',
      throughput: 'requests/second',
      errorRate: 'ratio',
    },
    configuration: {
      baseUrl: __ENV.BASE_URL,
      vus: __ENV.VUS,
      duration: __ENV.DURATION,
      hotsetPercent: __ENV.HOTSET_PERCENT,
      pauseMs: __ENV.PAUSE_MS,
    },
    metrics: data.metrics,
    rootGroup: data.root_group,
  };
  const concise = {
    label,
    metrics: selectedMetrics(data),
  };

  return {
    [`${outputDirectory}/${label}-summary.json`]: JSON.stringify(report, null, 2),
    stdout: `${JSON.stringify(concise, null, 2)}\n`,
  };
}
