/**
 * ブラウザパフォーマンス計測の共通ユーティリティ。
 *
 * k6（サーバ API の負荷試験）とは別に、ここでは「ブラウザ側の体感性能」を測る:
 *   - Navigation Timing / Paint Timing API による描画指標（DOMContentLoaded / load / FCP）
 *   - Playwright 操作の前後を実時間で挟んだ操作レイテンシ（ログイン→描画、モーダル開閉 など）
 *   - リソース転送量（リグレッション監視の参考値）
 *
 * 収集した指標はしきい値（budget）と比較し、results/browser-perf.{md,json} に出力する。
 */

import { writeFileSync, mkdirSync } from 'node:fs'
import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import type { Page } from '@playwright/test'

// このファイルの場所を基準に results/ ディレクトリのパスを解決する。
const HERE = dirname(fileURLToPath(import.meta.url))
const RESULTS_DIR = `${HERE}/../results`

/** 1 つの計測指標。budget があれば合否（pass）も判定する。 */
export interface Metric {
  name: string
  value: number
  unit: string
  budget?: number
  pass?: boolean
}

/** ブラウザの描画系タイミング指標（単位: ミリ秒、転送量はバイト）。 */
export interface NavigationMetrics {
  domContentLoaded: number | null
  load: number | null
  responseEnd: number | null
  firstContentfulPaint: number | null
  resourceCount: number
  transferSize: number
}

/**
 * 現在表示中のページから Navigation Timing / Paint Timing を取得する。
 * page.evaluate でブラウザ内の performance API を直接読み出す。
 */
export async function getNavigationMetrics(page: Page): Promise<NavigationMetrics> {
  return await page.evaluate(() => {
    const nav = performance.getEntriesByType('navigation')[0] as
      | PerformanceNavigationTiming
      | undefined
    const paint = performance.getEntriesByType('paint')
    const fcp = paint.find((p) => p.name === 'first-contentful-paint')?.startTime ?? null
    const resources = performance.getEntriesByType('resource') as PerformanceResourceTiming[]
    return {
      // startTime 起点（=ナビゲーション開始）からの相対ミリ秒。
      domContentLoaded: nav ? nav.domContentLoadedEventEnd - nav.startTime : null,
      load: nav ? nav.loadEventEnd - nav.startTime : null,
      responseEnd: nav ? nav.responseEnd - nav.startTime : null,
      firstContentfulPaint: fcp,
      resourceCount: resources.length,
      transferSize: resources.reduce((sum, r) => sum + (r.transferSize || 0), 0),
    }
  })
}

/**
 * 計測結果を貯めてレポート（md / json）に書き出すレコーダー。
 * perf.spec.ts で 1 インスタンスを共有し、全計測後にまとめて出力する。
 */
export class PerfReport {
  private metrics: Metric[] = []

  /**
   * 指標を 1 件追加する。budget を渡すと value <= budget で合否判定し、その結果を返す。
   * （単位がバイトなど「小さいほど良い」前提の指標を扱う）
   */
  add(name: string, value: number, unit: string, budget?: number): boolean | undefined {
    const pass = budget == null ? undefined : value <= budget
    this.metrics.push({ name, value: Math.round(value), unit, budget, pass })
    return pass
  }

  /** 人が読む Markdown サマリを組み立てる。 */
  toMarkdown(): string {
    const lines: string[] = []
    lines.push('# ブラウザパフォーマンス計測結果')
    lines.push('')
    lines.push('Playwright（Chromium）で計測した、ブラウザ体感性能の指標です。')
    lines.push('`budget`（しきい値）がある指標は合否を判定します（初回は実測のベースライン化が目的）。')
    lines.push('')
    lines.push('| 指標 | 実測 | 単位 | しきい値 | 判定 |')
    lines.push('| --- | ---: | --- | ---: | :---: |')
    for (const m of this.metrics) {
      const budget = m.budget == null ? '-' : String(m.budget)
      const verdict = m.pass == null ? '—' : m.pass ? '✅' : '❌'
      lines.push(`| ${m.name} | ${m.value} | ${m.unit} | ${budget} | ${verdict} |`)
    }
    lines.push('')
    return lines.join('\n')
  }

  /** results/browser-perf.{md,json} に書き出す。 */
  write(): void {
    mkdirSync(RESULTS_DIR, { recursive: true })
    writeFileSync(`${RESULTS_DIR}/browser-perf.json`, JSON.stringify(this.metrics, null, 2), 'utf-8')
    writeFileSync(`${RESULTS_DIR}/browser-perf.md`, this.toMarkdown(), 'utf-8')
  }

  /** budget 付き指標のうち 1 つでも未達があれば true（テスト失敗判定に使う）。 */
  hasFailure(): boolean {
    return this.metrics.some((m) => m.pass === false)
  }

  /** budget（しきい値）が設定されている指標だけを返す（テストの soft 判定に使う）。 */
  metricsWithBudget(): Metric[] {
    return this.metrics.filter((m) => m.budget != null)
  }
}
