/**
 * 左サイドバーナビゲーションコンポーネント。
 * ロゴ・ホーム・検索・プロフィール・投稿するボタン・ユーザー情報・ログアウトを表示する。
 */

import { Link } from 'react-router-dom'

interface Props {
  displayName: string
  username: string
  /** 投稿モーダルを開くコールバック */
  onOpenPostModal: () => void
  /** ログアウトコールバック */
  onLogout: () => void
}

export default function Sidebar({ displayName, username, onOpenPostModal, onLogout }: Props) {
  return (
    <aside style={styles.sidebar}>
      {/* ロゴ */}
      <div style={styles.logo}>RTL</div>

      {/* ナビゲーションメニュー */}
      <nav style={styles.nav}>
        <Link to="/home" style={{ ...styles.navItem, textDecoration: 'none', color: 'inherit' }}>
          <span style={styles.navIcon}>🏠</span>
          <span style={styles.navLabel}>ホーム</span>
        </Link>
        {/* 検索ページへのリンク */}
        <Link to="/search" style={{ ...styles.navItem, textDecoration: 'none', color: 'inherit' }}>
          <span style={styles.navIcon}>🔍</span>
          <span style={styles.navLabel}>検索</span>
        </Link>
        {/* プロフィールナビアイテム: 自分のプロフィールページへ遷移する */}
        <Link to={`/users/${username}`} style={{ ...styles.navItem, textDecoration: 'none', color: 'inherit' }}>
          <span style={styles.navIcon}>👤</span>
          <span style={styles.navLabel}>プロフィール</span>
        </Link>
      </nav>

      {/* 投稿するボタン */}
      <button style={styles.postButton} onClick={onOpenPostModal}>
        投稿する
      </button>

      {/* ユーザー情報 + ログアウト（下部に固定） */}
      <div style={styles.userSection}>
        {/* アバタークリックで自分のプロフィールページへ遷移する */}
        <Link to={`/users/${username}`} style={{ ...styles.userInfo, textDecoration: 'none', color: 'inherit' }}>
          <Avatar displayName={displayName} username={username} size={36} />
          <div>
            <div style={styles.userName}>{displayName}</div>
            <div style={styles.userHandle}>@{username}</div>
          </div>
        </Link>
        <button style={styles.logoutButton} onClick={onLogout}>
          ログアウト
        </button>
      </div>
    </aside>
  )
}

/**
 * ユーザーアバターコンポーネント。
 * avatarUrl がある場合はプロフィール画像（S3 の URL）を表示する。
 * avatarUrl がない場合は username のハッシュで色を決めたイニシャル丸にフォールバックする。
 */
export function Avatar({
  displayName,
  username,
  avatarUrl,
  size = 40,
}: {
  displayName: string
  username: string
  /** S3 に保存されたプロフィール画像 URL（未設定時は null → イニシャルで代替） */
  avatarUrl?: string | null
  size?: number
}) {
  // プロフィール画像がある場合はそちらを表示する
  if (avatarUrl) {
    return (
      <img
        src={avatarUrl}
        alt={`${displayName}のアバター`}
        style={{
          width: size,
          height: size,
          borderRadius: '50%',
          objectFit: 'cover',
          flexShrink: 0,
        }}
      />
    )
  }

  const colors = ['#1d9bf0', '#7856ff', '#00ba7c', '#ff7a00', '#f4212e', '#ff6b9d']
  // username の各文字コードの合計で色インデックスを決める
  const colorIndex =
    username.split('').reduce((acc, ch) => acc + ch.charCodeAt(0), 0) % colors.length
  const bg = colors[colorIndex]
  // 表示名の最初の1文字をイニシャルとして使う
  const initial = displayName.charAt(0).toUpperCase()

  return (
    <div
      style={{
        width: size,
        height: size,
        borderRadius: '50%',
        background: bg,
        color: '#fff',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: size * 0.4,
        fontWeight: 700,
        flexShrink: 0,
      }}
    >
      {initial}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  sidebar: {
    width: 260,
    flexShrink: 0,
    display: 'flex',
    flexDirection: 'column',
    padding: '16px 24px',
    borderRight: '1px solid #e1e8ed',
    height: '100vh',
    position: 'sticky',
    top: 0,
    boxSizing: 'border-box',
  },
  logo: {
    fontSize: 28,
    fontWeight: 900,
    color: '#1d9bf0',
    letterSpacing: -1,
    marginBottom: 24,
    paddingLeft: 8,
  },
  nav: {
    display: 'flex',
    flexDirection: 'column',
    gap: 4,
    marginBottom: 24,
  },
  navItem: {
    display: 'flex',
    alignItems: 'center',
    gap: 16,
    padding: '12px 8px',
    borderRadius: 9999,
    cursor: 'pointer',
    transition: 'background 0.15s',
    fontSize: 19,
    fontWeight: 600,
    color: '#0f1419',
  },
  navIcon: {
    fontSize: 22,
    width: 28,
    textAlign: 'center',
  },
  navLabel: {
    fontSize: 17,
  },
  postButton: {
    padding: '14px 0',
    background: '#1d9bf0',
    color: '#ffffff',
    border: 'none',
    borderRadius: 9999,
    fontSize: 15,
    fontWeight: 700,
    cursor: 'pointer',
    width: '100%',
    marginBottom: 'auto',
  },
  userSection: {
    marginTop: 'auto',
    paddingTop: 16,
    display: 'flex',
    flexDirection: 'column',
    gap: 8,
  },
  userInfo: {
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    padding: '8px',
    borderRadius: 12,
    background: '#f7f9f9',
  },
  userName: {
    fontWeight: 700,
    fontSize: 14,
    color: '#0f1419',
  },
  userHandle: {
    fontSize: 13,
    color: '#536471',
  },
  logoutButton: {
    padding: '8px 0',
    background: 'transparent',
    color: '#f4212e',
    border: '1px solid #f4212e',
    borderRadius: 9999,
    fontSize: 13,
    fontWeight: 700,
    cursor: 'pointer',
    width: '100%',
  },
}
