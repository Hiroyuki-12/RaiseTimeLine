# 要件定義書

## アプリ概要

**RaiseTimeLine** は、X（旧Twitter）のタイムライン形式をベースにした学習目的のSNSアプリケーションです。
複数ユーザーが利用することを前提とし、投稿・コメント・いいね・フォローなどの基本的なSNS機能を提供します。

## 目的

- SNSアプリの設計・実装パターンを学習する
- React + Spring Boot + PostgreSQL + AWS の実践的な組み合わせを習得する

## 対象ユーザー

- AIエンジニアコース受講生・個人開発者
- 複数ユーザーが同時に利用することを想定

## 差別化ポイント（X/Twitterとの違い）

| 項目 | X/Twitter | RaiseTimeLine |
|------|-----------|---------------|
| インプレッション数 | 表示あり | **表示なし** |
| リツイート | あり | **なし** |
| タイムライン | 全体のみ | **全体 + フォロー中** |
| 画像投稿 | 複数枚 | **1投稿1枚まで** |

---

## 機能要件一覧

| 機能ID | 機能名 | 概要 | 詳細 |
|--------|--------|------|------|
| F-01 | ログイン・ユーザー登録 | メールアドレス＋パスワードで認証 | [auth.md](features/auth.md) |
| F-02 | タイムライン | 全体／フォロー中の2種類のタイムライン | [timeline.md](features/timeline.md) |
| F-03 | 投稿 | テキスト＋画像（1枚）の投稿・削除 | [post.md](features/post.md) |
| F-04 | コメント | 投稿へのコメント投稿・削除・件数表示 | [comment.md](features/comment.md) |
| F-05 | いいね | 投稿へのいいね追加・取消・件数表示 | [like.md](features/like.md) |
| F-06 | プロフィール | ユーザー情報の表示・編集・投稿一覧 | [profile.md](features/profile.md) |
| F-07 | フォロー/フォロワー | フォロー・アンフォロー・一覧表示 | [follow.md](features/follow.md) |
| F-08 | ユーザー検索 | ユーザー名によるインクリメンタル検索 | [search.md](features/search.md) |

---

## スコープ外（実装しない機能）

- インプレッション（閲覧数）の計測・表示
- リツイート / 引用ポスト
- DM（ダイレクトメッセージ）
- 通知機能
- ハッシュタグ・トレンド
- 広告機能

---

## 制約・前提条件

- 画像は1投稿につき1枚まで（AWS S3に保存）
- 認証方式はJWT（メールアドレス＋パスワード）のみ
- いいねは1ユーザー1投稿につき1回まで
- 投稿削除は投稿者本人のみ可能
- コメント削除はコメント投稿者本人のみ可能
- プロフィール編集は本人のみ可能
- 自分自身をフォローすることはできない

---

## ドキュメントインデックス

| ドキュメント | 内容 |
|------------|------|
| [requirements.md](requirements.md) | 本ドキュメント（要件定義書） |
| [non-functional-requirements.md](non-functional-requirements.md) | 非機能要件 |
| [screen-list.md](screen-list.md) | 画面一覧 |
| [screen-design.md](screen-design.md) | 画面設計書 |
| [er-diagram.md](er-diagram.md) | ER図・テーブル定義 |
| [aws-architecture.md](aws-architecture.md) | AWSインフラ構成 |
| [tech-stack.md](tech-stack.md) | 技術スタック |
| [features/auth.md](features/auth.md) | 機能定義：ログイン・ユーザー登録 |
| [features/timeline.md](features/timeline.md) | 機能定義：タイムライン |
| [features/post.md](features/post.md) | 機能定義：投稿 |
| [features/comment.md](features/comment.md) | 機能定義：コメント |
| [features/like.md](features/like.md) | 機能定義：いいね |
| [features/profile.md](features/profile.md) | 機能定義：プロフィール |
| [features/follow.md](features/follow.md) | 機能定義：フォロー/フォロワー |
| [features/search.md](features/search.md) | 機能定義：ユーザー検索 |
