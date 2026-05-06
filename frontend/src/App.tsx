/**
 * アプリのルーティング設定。
 * React Router v6 の BrowserRouter を使ってページ遷移を管理する。
 *
 * ルート一覧:
 * /              → /login にリダイレクト
 * /login         → ログイン画面
 * /register      → 新規登録画面
 * /home          → ログイン後のホーム画面（未認証時は /login へ）
 * /posts/:postId → 投稿詳細・コメント画面（未認証時は /login へ）
 */

import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import HomePage from './pages/HomePage'
import PostDetailPage from './pages/PostDetailPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* ルートアクセスはログイン画面へリダイレクト */}
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/home" element={<HomePage />} />
        {/* 投稿詳細・コメントページ */}
        <Route path="/posts/:postId" element={<PostDetailPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
