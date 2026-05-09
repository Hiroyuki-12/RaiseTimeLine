/**
 * RegisterPage の単体テスト。
 *
 * 対象: RegisterPage
 * 技法: デシジョンテーブル (200 / 400 errors / 400 message / 409 message)
 */
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import RegisterPage from './RegisterPage'
import { server } from '../test/server'

function renderWithRouter() {
  return render(
    <MemoryRouter initialEntries={['/register']}>
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/home" element={<div data-testid="home-page">HOME</div>} />
      </Routes>
    </MemoryRouter>
  )
}

async function fillForm() {
  await userEvent.type(screen.getByLabelText('表示名'), 'アリス')
  await userEvent.type(screen.getByLabelText('ユーザー名（@handle）'), 'alice')
  await userEvent.type(screen.getByLabelText('メールアドレス'), 'a@example.com')
  await userEvent.type(screen.getByLabelText('パスワード'), 'Pass1234')
  await userEvent.type(screen.getByLabelText('パスワード（確認）'), 'Pass1234')
}

describe('RegisterPage', () => {
  it('正常登録時は /home に遷移する', async () => {
    renderWithRouter()
    await fillForm()

    await userEvent.click(screen.getByRole('button', { name: '新規登録' }))

    await waitFor(() => expect(screen.getByTestId('home-page')).toBeInTheDocument())
  })

  it('バリデーションエラー (errors マップ) はフィールドごとに表示される', async () => {
    server.use(
      http.post('/api/auth/register', () =>
        HttpResponse.json(
          {
            errors: {
              email: '有効なメールアドレスを入力してください',
              password: 'パスワードは8文字以上で入力してください',
            },
          },
          { status: 400 }
        )
      ),
    )
    renderWithRouter()
    await fillForm()

    await userEvent.click(screen.getByRole('button', { name: '新規登録' }))

    await waitFor(() =>
      expect(screen.getByText('有効なメールアドレスを入力してください')).toBeInTheDocument()
    )
    expect(screen.getByText('パスワードは8文字以上で入力してください')).toBeInTheDocument()
  })

  it('メール重複 (message) は全体エラーとして表示される', async () => {
    server.use(
      http.post('/api/auth/register', () =>
        HttpResponse.json({ message: 'このメールアドレスは既に使用されています' }, { status: 400 })
      )
    )
    renderWithRouter()
    await fillForm()

    await userEvent.click(screen.getByRole('button', { name: '新規登録' }))

    await waitFor(() =>
      expect(screen.getByText('このメールアドレスは既に使用されています')).toBeInTheDocument()
    )
  })
})
