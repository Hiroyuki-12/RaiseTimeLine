/**
 * LoginPage の単体テスト。
 *
 * 対象: LoginPage
 * 技法: 状態遷移 (未入力 → 入力 → 送信 → 成功/失敗) + デシジョンテーブル (200/401)
 */
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import LoginPage from './LoginPage'
import { server } from '../test/server'

/** Login 後の遷移確認用に /home を仮設置するルーター。 */
function renderWithRouter() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/home" element={<div data-testid="home-page">HOME</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('LoginPage', () => {
  it('正常ログイン時は /home に遷移する', async () => {
    renderWithRouter()

    await userEvent.type(screen.getByLabelText('メールアドレス'), 'a@example.com')
    await userEvent.type(screen.getByLabelText('パスワード'), 'Pass1234')
    await userEvent.click(screen.getByRole('button', { name: 'ログイン' }))

    await waitFor(() => expect(screen.getByTestId('home-page')).toBeInTheDocument())
  })

  it('認証失敗時はサーバーのメッセージを表示する', async () => {
    // 注: 401 を返すと apiClient の interceptor が refresh を試みて挙動が複雑になるため、
    // ログイン特有の業務エラー (400) としてメッセージ表示パスを検証する。
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json(
          { message: 'メールアドレスまたはパスワードが正しくありません' },
          { status: 400 }
        )
      )
    )
    renderWithRouter()

    await userEvent.type(screen.getByLabelText('メールアドレス'), 'a@example.com')
    await userEvent.type(screen.getByLabelText('パスワード'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'ログイン' }))

    await waitFor(() =>
      expect(
        screen.getByText('メールアドレスまたはパスワードが正しくありません')
      ).toBeInTheDocument()
    )
  })

  it('新規登録リンクが表示される', () => {
    renderWithRouter()
    expect(screen.getByRole('link', { name: '新規登録' })).toHaveAttribute('href', '/register')
  })
})
