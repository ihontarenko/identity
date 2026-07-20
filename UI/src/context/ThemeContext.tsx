import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react"
import {
  allDarkThemes,
  allLightThemes,
  FONT_SCALE_VALUES,
  findTheme,
  type ContrastMode,
  type FontScale,
  type SeasonalEffect,
  type ThemeMode,
} from "@/theming"

// Ported from Moneta/UI's ThemeContext (itself ported from Innoventa's themeStore.ts) —
// same mechanism, same 27 themes, just Identity's own localStorage namespace.
const THEME_MODE_STORAGE_KEY = "identity.theme-mode"
const LIGHT_THEME_STORAGE_KEY = "identity.light-theme"
const DARK_THEME_STORAGE_KEY = "identity.dark-theme"
const FONT_SCALE_STORAGE_KEY = "identity.font-scale"
const CONTRAST_MODE_STORAGE_KEY = "identity.contrast-mode"
const SEASONAL_EFFECT_STORAGE_KEY = "identity.seasonal-effect-enabled"

const defaultLightTheme = allLightThemes[0].name
const defaultDarkTheme = allDarkThemes[0].name

const CONTRAST_TIERS: [cssVariables: string[], factorIndex: number][] = [
  [["--foreground", "--card-foreground", "--popover-foreground", "--sidebar-foreground"], 0],
  [["--secondary-foreground"], 1],
  [["--muted-foreground"], 2],
  [["--ink-4"], 3],
]

interface ThemeContextValue {
  mode: ThemeMode
  lightTheme: string
  darkTheme: string
  resolvedTheme: string
  activeEffect: SeasonalEffect | undefined
  fontScale: FontScale
  contrastMode: ContrastMode
  seasonalEffectEnabled: boolean
  setMode: (mode: ThemeMode) => void
  setLightTheme: (themeName: string) => void
  setDarkTheme: (themeName: string) => void
  setFontScale: (scale: FontScale) => void
  setContrastMode: (mode: ContrastMode) => void
  setSeasonalEffectEnabled: (enabled: boolean) => void
}

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined)

function prefersDarkColorScheme() {
  return window.matchMedia("(prefers-color-scheme: dark)").matches
}

function blendHex(hex: string, target: number, factor: number): string {
  const cleaned = hex.replace("#", "").padEnd(6, "0")
  const value = Number.parseInt(cleaned, 16)
  const red = Math.round(((value >> 16) & 0xff) * (1 - factor) + target * factor)
  const green = Math.round(((value >> 8) & 0xff) * (1 - factor) + target * factor)
  const blue = Math.round((value & 0xff) * (1 - factor) + target * factor)
  return "#" + [red, green, blue].map((channel) => channel.toString(16).padStart(2, "0")).join("")
}

function applyContrastMode(mode: ContrastMode, isDarkTheme: boolean) {
  const root = document.documentElement
  for (const [cssVariables] of CONTRAST_TIERS) {
    for (const cssVariable of cssVariables) {
      root.style.removeProperty(cssVariable)
    }
  }
  if (mode === "normal") {
    return
  }
  const factors = mode === "medium" ? [0.05, 0.15, 0.28, 0.42] : [0.1, 0.25, 0.45, 0.6]
  const target = isDarkTheme ? 255 : 0
  const computed = getComputedStyle(root)
  for (const [cssVariables, factorIndex] of CONTRAST_TIERS) {
    const factor = factors[factorIndex]
    for (const cssVariable of cssVariables) {
      const currentValue = computed.getPropertyValue(cssVariable).trim()
      if (currentValue.startsWith("#")) {
        root.style.setProperty(cssVariable, blendHex(currentValue, target, factor))
      }
    }
  }
}

function isMobileDevice() {
  return /Android|iPhone|iPad|iPod/i.test(navigator.userAgent) || ("ontouchstart" in window && window.innerWidth < 1024)
}

function applyFontScale(scale: FontScale) {
  const value = FONT_SCALE_VALUES[scale]
  document.documentElement.style.setProperty("--font-scale", String(value))
  if (isMobileDevice()) {
    return
  }
  ;(document.body.style as CSSStyleDeclaration & { zoom: string }).zoom = String(value)
}

interface ThemeProviderProperties {
  children: ReactNode
}

export function ThemeProvider({ children }: ThemeProviderProperties) {
  const [mode, setModeState] = useState<ThemeMode>(
    () => (localStorage.getItem(THEME_MODE_STORAGE_KEY) as ThemeMode | null) ?? "system",
  )
  const [lightTheme, setLightThemeState] = useState(
    () => localStorage.getItem(LIGHT_THEME_STORAGE_KEY) ?? defaultLightTheme,
  )
  const [darkTheme, setDarkThemeState] = useState(
    () => localStorage.getItem(DARK_THEME_STORAGE_KEY) ?? defaultDarkTheme,
  )
  const [fontScale, setFontScaleState] = useState<FontScale>(
    () => (localStorage.getItem(FONT_SCALE_STORAGE_KEY) as FontScale | null) ?? "medium",
  )
  const [contrastMode, setContrastModeState] = useState<ContrastMode>(
    () => (localStorage.getItem(CONTRAST_MODE_STORAGE_KEY) as ContrastMode | null) ?? "normal",
  )
  const [seasonalEffectEnabled, setSeasonalEffectEnabledState] = useState(
    () => localStorage.getItem(SEASONAL_EFFECT_STORAGE_KEY) !== "false",
  )
  const [systemPrefersDark, setSystemPrefersDark] = useState(prefersDarkColorScheme)

  useEffect(() => {
    const mediaQueryList = window.matchMedia("(prefers-color-scheme: dark)")
    const onChange = () => setSystemPrefersDark(mediaQueryList.matches)
    mediaQueryList.addEventListener("change", onChange)
    return () => mediaQueryList.removeEventListener("change", onChange)
  }, [])

  const isDark = mode === "dark" || (mode === "system" && systemPrefersDark)
  const resolvedTheme = isDark ? darkTheme : lightTheme
  const activeEffect = findTheme(resolvedTheme)?.effect

  useEffect(() => {
    const root = document.documentElement
    for (const theme of [...allLightThemes, ...allDarkThemes]) {
      root.classList.remove(theme.name)
    }
    root.classList.add(resolvedTheme)
    root.classList.toggle("dark", isDark)
    applyContrastMode(contrastMode, isDark)
  }, [resolvedTheme, isDark, contrastMode])

  useEffect(() => {
    applyFontScale(fontScale)
  }, [fontScale])

  const setMode = (nextMode: ThemeMode) => {
    localStorage.setItem(THEME_MODE_STORAGE_KEY, nextMode)
    setModeState(nextMode)
  }

  const setLightTheme = (themeName: string) => {
    localStorage.setItem(LIGHT_THEME_STORAGE_KEY, themeName)
    setLightThemeState(themeName)
    setMode("light")
  }

  const setDarkTheme = (themeName: string) => {
    localStorage.setItem(DARK_THEME_STORAGE_KEY, themeName)
    setDarkThemeState(themeName)
    setMode("dark")
  }

  const setFontScale = (scale: FontScale) => {
    localStorage.setItem(FONT_SCALE_STORAGE_KEY, scale)
    setFontScaleState(scale)
  }

  const setContrastMode = (nextContrastMode: ContrastMode) => {
    localStorage.setItem(CONTRAST_MODE_STORAGE_KEY, nextContrastMode)
    setContrastModeState(nextContrastMode)
  }

  const setSeasonalEffectEnabled = (enabled: boolean) => {
    localStorage.setItem(SEASONAL_EFFECT_STORAGE_KEY, String(enabled))
    setSeasonalEffectEnabledState(enabled)
  }

  const value = useMemo<ThemeContextValue>(
    () => ({
      mode,
      lightTheme,
      darkTheme,
      resolvedTheme,
      activeEffect,
      fontScale,
      contrastMode,
      seasonalEffectEnabled,
      setMode,
      setLightTheme,
      setDarkTheme,
      setFontScale,
      setContrastMode,
      setSeasonalEffectEnabled,
    }),
    [mode, lightTheme, darkTheme, resolvedTheme, activeEffect, fontScale, contrastMode, seasonalEffectEnabled],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  const context = useContext(ThemeContext)

  if (!context) {
    throw new Error("useTheme must be used within a ThemeProvider")
  }

  return context
}
