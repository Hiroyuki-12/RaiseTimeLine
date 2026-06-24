// ============================================================================
// k6 結果レポート生成ヘルパー
//
// k6 は既定だと結果をコンソールに表示するだけでファイルが残らない。
// そこで handleSummary フックを使い、実行後に
//   ・perf/results/<name>.md   … 人が読む Markdown レポート
//   ・perf/results/<name>.json … 生データ (機械処理・差分比較用)
//   ・stdout                   … 従来どおりコンソール表示
// を出力する。各シナリオはこの makeHandleSummary を使って handleSummary を export する。
//
// なぜ Markdown を自前生成するか:
//   外部の k6 サマリ用ライブラリは実行時にネットワーク取得が必要で、オフラインだと
//   失敗する。レポートは確実に出したいので、依存ゼロで data.metrics から組み立てる。
// ============================================================================

// 数値を小数 2 桁 + ms で整形する (ミリ秒メトリクス用)
function ms(v) {
  if (v === undefined || v === null) return '-';
  return `${v.toFixed(2)}ms`;
}

// メトリクスの p95 など特定の統計値を安全に取り出す
function stat(metric, key) {
  if (!metric || !metric.values || metric.values[key] === undefined) return undefined;
  return metric.values[key];
}

// しきい値 (thresholds) の合否を判定する。1 つでも未達なら false。
function thresholdsPassed(data) {
  let allOk = true;
  const rows = [];
  for (const name of Object.keys(data.metrics)) {
    const m = data.metrics[name];
    if (!m.thresholds) continue;
    for (const expr of Object.keys(m.thresholds)) {
      const ok = m.thresholds[expr].ok;
      if (!ok) allOk = false;
      rows.push({ metric: name, expr, ok });
    }
  }
  return { allOk, rows };
}

// data から Markdown レポート本文を組み立てる
function toMarkdown(testName, data) {
  const checks = data.metrics.checks;
  const checkRate = stat(checks, 'rate');
  const reqs = data.metrics.http_reqs;
  const dur = data.metrics.http_req_duration;
  const failed = data.metrics.http_req_failed;
  const iterations = data.metrics.iterations;

  const { allOk, rows } = thresholdsPassed(data);

  const lines = [];
  lines.push(`# パフォーマンステスト結果: ${testName}`);
  lines.push('');
  lines.push(`- 総合判定: ${allOk ? '✅ PASS (全しきい値クリア)' : '❌ FAIL (しきい値未達あり)'}`);
  if (checkRate !== undefined) {
    lines.push(`- チェック成功率: ${(checkRate * 100).toFixed(2)}%`);
  }
  if (failed) {
    lines.push(`- リクエスト失敗率: ${(stat(failed, 'rate') * 100).toFixed(2)}%`);
  }
  if (reqs) {
    lines.push(`- 総リクエスト数: ${stat(reqs, 'count')} (${(stat(reqs, 'rate') || 0).toFixed(2)} req/s)`);
  }
  if (iterations) {
    lines.push(`- イテレーション数: ${stat(iterations, 'count')}`);
  }
  lines.push('');

  // 全体のレイテンシ
  if (dur) {
    lines.push('## レスポンスタイム (全体)');
    lines.push('');
    lines.push('| avg | med | p90 | p95 | max |');
    lines.push('| --- | --- | --- | --- | --- |');
    lines.push(
      `| ${ms(stat(dur, 'avg'))} | ${ms(stat(dur, 'med'))} | ${ms(stat(dur, 'p(90)'))} | ${ms(stat(dur, 'p(95)'))} | ${ms(stat(dur, 'max'))} |`,
    );
    lines.push('');
  }

  // エンドポイント別レイテンシ (tags: {name: ...} で集計したサブメトリクス)
  // k6 の sub-metric は "http_req_duration{name:GET /api/posts (all)}" という名前で入る。
  const endpointRows = [];
  for (const key of Object.keys(data.metrics)) {
    const match = key.match(/^http_req_duration\{name:(.+)\}$/);
    if (match) {
      endpointRows.push({ name: match[1], metric: data.metrics[key] });
    }
  }
  if (endpointRows.length > 0) {
    lines.push('## レスポンスタイム (エンドポイント別)');
    lines.push('');
    lines.push('| エンドポイント | avg | p95 | max |');
    lines.push('| --- | --- | --- | --- |');
    for (const r of endpointRows) {
      lines.push(
        `| ${r.name} | ${ms(stat(r.metric, 'avg'))} | ${ms(stat(r.metric, 'p(95)'))} | ${ms(stat(r.metric, 'max'))} |`,
      );
    }
    lines.push('');
  }

  // しきい値の内訳
  if (rows.length > 0) {
    lines.push('## しきい値 (thresholds) 判定');
    lines.push('');
    lines.push('| メトリクス | 条件 | 判定 |');
    lines.push('| --- | --- | --- |');
    for (const r of rows) {
      lines.push(`| ${r.metric} | \`${r.expr}\` | ${r.ok ? '✅ PASS' : '❌ FAIL'} |`);
    }
    lines.push('');
  }

  lines.push('---');
  lines.push('');
  lines.push('生成: k6 handleSummary (perf/k6/lib/summary.js)');
  lines.push('');
  return lines.join('\n');
}

// 簡易なコンソール表示用テキスト (既定サマリの代わり)
function toStdout(testName, data) {
  const { allOk } = thresholdsPassed(data);
  const dur = data.metrics.http_req_duration;
  const failed = data.metrics.http_req_failed;
  const verdict = allOk ? 'PASS ✅' : 'FAIL ❌';
  const p95 = dur ? ms(stat(dur, 'p(95)')) : '-';
  const failRate = failed ? `${(stat(failed, 'rate') * 100).toFixed(2)}%` : '-';
  return (
    `\n==== ${testName} 結果 ====\n` +
    `総合判定 : ${verdict}\n` +
    `p95      : ${p95}\n` +
    `失敗率   : ${failRate}\n` +
    `レポート : perf/results/${testName}.md / .json\n`
  );
}

// 各シナリオが export する handleSummary を生成する。
// testName はシナリオ名 (例: 'timeline')。出力ファイル名に使う。
export function makeHandleSummary(testName) {
  return function (data) {
    const out = {};
    // コンソールには簡易サマリを表示
    out['stdout'] = toStdout(testName, data);
    // Markdown レポートと生 JSON をファイル出力
    out[`perf/results/${testName}.md`] = toMarkdown(testName, data);
    out[`perf/results/${testName}.json`] = JSON.stringify(data, null, 2);
    return out;
  };
}
