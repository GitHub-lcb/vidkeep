/**
 * 主题：forest 浅色 / 深色（Qoder CN 设计规范）
 * - 首屏由 index.html 内联脚本提前写入 data-theme，避免闪烁
 * - 运行期通过 data-theme 切换 CSS 变量，并持久化到 localStorage
 */
export type ThemeName = 'light' | 'dark'

const STORAGE_KEY = 'vidkeep_theme'
const THEME_EVENT = 'vidkeep:theme'

interface ViewTransitionDocument extends Document {
  startViewTransition?: (callback: () => void) => void
}

export function getTheme(): ThemeName {
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
}

export function applyTheme(theme: ThemeName): void {
  document.documentElement.dataset.theme = theme
}

export function resolveInitialTheme(): ThemeName {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark') return saved
  } catch {
    // 隐私模式下忽略
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function initTheme(): ThemeName {
  const theme = resolveInitialTheme()
  applyTheme(theme)
  return theme
}

/**
 * 切换主题；支持 View Transition 的浏览器做 160ms 交叉淡入。
 */
export function toggleTheme(): ThemeName {
  const next: ThemeName = getTheme() === 'dark' ? 'light' : 'dark'
  const persist = (): void => {
    applyTheme(next)
    try {
      window.localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // 忽略写入失败
    }
    window.dispatchEvent(new CustomEvent(THEME_EVENT, { detail: next }))
  }
  const viewTransition = (document as ViewTransitionDocument).startViewTransition
  if (typeof viewTransition === 'function') {
    viewTransition.call(document, persist)
  } else {
    persist()
  }
  return next
}

/**
 * 订阅主题变化（ThemeToggle 等组件使用）。
 */
export function onThemeChange(handler: (theme: ThemeName) => void): () => void {
  const listener = (event: Event): void => {
    const detail = (event as CustomEvent<ThemeName>).detail
    handler(detail === 'dark' ? 'dark' : 'light')
  }
  window.addEventListener(THEME_EVENT, listener)
  return () => window.removeEventListener(THEME_EVENT, listener)
}
