/**
 * テスト基盤のスモークテスト。
 *
 * Vitest が起動するか / jest-dom の拡張が読み込まれているか / MSW が動くか を最低限確認する。
 * Issue F でコンポーネント・API テストを追加する前段階の動作確認用。
 */
describe('テスト基盤スモーク', () => {
  it('expect が使える (globals: true 設定の確認)', () => {
    expect(1 + 1).toBe(2)
  })

  it('jsdom が DOM を提供する (environment: jsdom の確認)', () => {
    const div = document.createElement('div')
    div.textContent = 'hello'
    document.body.appendChild(div)
    expect(div).toBeInTheDocument()
    expect(div).toHaveTextContent('hello')
  })

  it('MSW がインターセプトしてダミーレスポンスを返す', async () => {
    const res = await fetch('/api/posts')
    expect(res.status).toBe(200)
    const body = await res.json()
    expect(body).toEqual([])
  })
})
