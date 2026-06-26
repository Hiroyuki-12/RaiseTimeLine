import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
// アクセシビリティ（a11y）の静的解析プラグイン。
// JSX 上の alt 欠落・不正な aria 属性・キーボード操作できない要素などを
// コードを書いた段階（CI の lint）で検出し、画面を開く前に作り込みを防ぐ。
import jsxA11y from 'eslint-plugin-jsx-a11y'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
      // jsx-a11y の推奨ルールセット（flat config 版）。
      // WCAG に沿った基本的なアクセシビリティ違反を warning/error として検出する。
      jsxA11y.flatConfigs.recommended,
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
])
