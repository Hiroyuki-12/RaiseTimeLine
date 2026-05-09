# RaiseTimeLine 運用ルール (Claude Code 用)

このリポジトリで Claude Code が作業するとき、および人間の開発者が作業するときの運用ルール。

## 大原則

- **`main` に直接 push しない**。常に Issue → ブランチ → PR → マージ の順。
- 作業開始前に必ず **Issue を起票** する。コードを書き始める前に Issue 番号を取得すること。
- 1 Issue = 1 ブランチ = 1 PR を基本とする。

## ブランチ命名規則

形式: `<type>/#<issue>-<slug>`

例:
- `feat/#12-add-login-form`
- `fix/#34-post-delete-500`
- `docs/#7-update-readme`

| type | 用途 |
| --- | --- |
| `feat` | 新機能追加 |
| `fix` | バグ修正 |
| `docs` | ドキュメントのみの変更 |
| `refactor` | 振る舞いを変えないリファクタ |
| `chore` | ビルド・依存・設定など雑務 |
| `test` | テスト追加・修正 |

- `#<issue>` は紐づく Issue 番号 (必須)
- `<slug>` は半角英小文字 + ハイフンの短い説明

## Issue 起票

新しい作業は GitHub の Issue テンプレートから起票する。

- Feature / Bug / Task / Docs の 4 種類
- 空 Issue (blank) は無効化されている

## Pull Request 規約

- PR 本文に `Closes #<issue>` を必ず含める (マージ時に Issue が自動クローズされる)
- PR 上のレビューコメント / 会話は **全解決してからマージ** (GitHub 側で強制)
- マージ方式は **squash または rebase のみ** (merge commit 禁止 / 線形履歴必須)
- マージ後はブランチを自動削除する設定

## main ブランチ保護 (GitHub 側で強制されている内容)

- 直接 push 禁止 (PR 必須)
- 会話解決必須
- 線形履歴必須
- force push 禁止 / ブランチ削除禁止
- 管理者にも適用

## ポート運用ルール（厳守）

このプロジェクトで使用するポートは以下に固定する。**別ポートでの代替起動は禁止**（プロキシ設定や URL 前提が崩れて動作確認にならないため）。

| サービス | ポート |
| --- | --- |
| フロントエンド (Vite dev server) | `5173` |
| バックエンド (Spring Boot) | `8080` |
| PostgreSQL | `5432` |

サーバー起動時にポートが競合していた場合の対応:

1. `lsof -i :<port>` で占有プロセスを特定する
2. **そのプロセスを停止する**（`kill <PID>` / `docker stop <container>` / `./gradlew --stop` 等、適切な手段で）
3. その上で本来のポートで起動し直す

絶対に行ってはいけないこと:

- `--port 5174` 等で別ポートに逃げる
- `server.port` を一時的に変更する
- ポート競合を放置したまま「動作確認できなかった」と報告する

ユーザーが明示的に別ポートを指示した場合のみ、その指示に従う。

## コードコメントルール

このプロジェクトのコードには、未経験者でも読んで理解できる日本語コメントを必ず記載すること。

- **すべてのクラス・メソッド・設定ブロック**にコメントを書く
- 「何をしているか」だけでなく「なぜそうしているか」も書く
- 特にセキュリティに関わる実装（BCrypt、JWT、CSRF、CORS 等）は理由を必ずコメントで説明する
- コメントは短くても良いが、初めて見る人が「なるほど」と思えるレベルに書く

例（悪い）: `// パスワードをハッシュ化する`
例（良い）: `// BCrypt でパスワードをハッシュ化する。平文のままDBに保存すると漏洩時に悪用されるため、元に戻せない一方向ハッシュで保存する`

## 品質チェックコマンド

コードを書いたら必ず以下を実行してエラーを確認する。

| 対象 | コマンド | 内容 |
| --- | --- | --- |
| フロントエンド（型チェック） | `cd frontend && npx tsc --noEmit` | TypeScript 型エラーの検出 |
| フロントエンド（lint） | `cd frontend && npx eslint src/` | ESLint ルール違反の検出 |
| フロントエンド（テスト） | `cd frontend && npm test` | Vitest によるユニット / 統合テスト |
| バックエンド（フォーマット確認） | `cd backend && ./gradlew spotlessCheck` | google-java-format 準拠チェック |
| バックエンド（フォーマット修正） | `cd backend && ./gradlew spotlessApply` | フォーマット違反の自動修正 |
| バックエンド（コンパイル） | `cd backend && ./gradlew compileJava` | Java コンパイルエラーの検出 |
| バックエンド（テスト） | `cd backend && ./gradlew test` | JUnit 5 / Mockito / Testcontainers によるテスト |

## テスト駆動の運用ルール（常時適用）

**実装と同じ PR にテストも必ず含める**。あとから別 Issue でテストをまとめるやり方はしない。

| 追加・変更した本番コード | 同じ PR に追加すべきテスト |
| --- | --- |
| Backend Service クラス | `*ServiceTest`（JUnit 5 + Mockito で分岐網羅） |
| Backend Controller クラス | `*ControllerTest`（MockMvcBuilders.standaloneSetup スライス） |
| Backend Mapper / SQL (XML) | `*MapperTest`（Testcontainers + 本番 PostgreSQL イメージ） |
| Frontend API クライアント | `src/api/*.test.ts`（MSW でモック） |
| Frontend コンポーネント | `src/components/*.test.tsx`（@testing-library/react） |
| Frontend ページ | `src/pages/*.test.tsx`（MemoryRouter + MSW） |

ケース導出の技法も使い分けること（同値分割 / 境界値 / デシジョンテーブル / 状態遷移 / 分岐網羅）。各テストクラス先頭に「対象・技法・ケース」をコメントで残すこと。

**テストを書いていない実装変更を PR に含めてはいけない**。リファクタリングや単純な型修正など例外的なケースで省略する場合は、PR 本文に理由を明記する。

## Claude Code への指示

ユーザーから作業を依頼されたときは、以下のフローを踏むこと。

1. 該当する Issue が無ければ、適切なテンプレートで Issue を作成する (`gh issue create`)
2. 上記命名規則に従ってブランチを切る
3. コードを実装する
4. **品質チェックを実施する**（上記コマンドをすべて通す。エラーがあれば修正する）
5. **動作確認を実施する**（サーバーを起動して実際に API や画面の動作を確認する）
6. **ユーザーに実装内容と確認結果を報告し、承認を得る**
7. 変更をコミットして push
8. `gh pr create` で PR を作成し、本文に `Closes #<issue>` を入れる
9. `main` への直接コミット / push は絶対に行わない

**手順 4〜6 を省略して PR を作成してはいけない。**  
品質チェックと動作確認の結果をユーザーに見せ、問題ないことを確認してから PR を作成する。
