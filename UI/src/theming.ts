export type ThemeCategory = "existing" | "palette" | "seasonal"
export type SeasonalEffect = "snow" | "hearts" | "clovers" | "skulls"

export interface ThemeDefinition {
  name: string
  label: string
  swatchColor: string
  category: ThemeCategory
  dark: boolean
  effect?: SeasonalEffect
}

// Full palette parity with Innoventa (src/stores/themeStore.ts) — same 27 themes, same ids,
// same accent used for the swatch dot. See index.css for the palette values themselves.
export const lightThemes: ThemeDefinition[] = [
  { name: "cream", label: "Cream", swatchColor: "#1E78A4", category: "existing", dark: false },
  { name: "sage", label: "Sage", swatchColor: "#2E7D52", category: "existing", dark: false },
  { name: "slate", label: "Slate", swatchColor: "#1565C0", category: "existing", dark: false },
  { name: "sand", label: "Sand", swatchColor: "#C05020", category: "existing", dark: false },
  { name: "rose", label: "Rose", swatchColor: "#C62060", category: "existing", dark: false },
  { name: "lavender", label: "Lavender", swatchColor: "#6B3FCC", category: "existing", dark: false },
  { name: "terracotta", label: "Terracotta", swatchColor: "#C2410C", category: "palette", dark: false },
  { name: "periwinkle", label: "Periwinkle", swatchColor: "#4F46E5", category: "palette", dark: false },
  { name: "nordic-ice", label: "Nordic Ice", swatchColor: "#2563EB", category: "palette", dark: false },
  { name: "monochrome", label: "Monochrome", swatchColor: "#111827", category: "palette", dark: false },
]

export const darkThemes: ThemeDefinition[] = [
  { name: "steel", label: "Steel", swatchColor: "#F0A030", category: "existing", dark: true },
  { name: "indigo", label: "Indigo", swatchColor: "#FF6B2B", category: "existing", dark: true },
  { name: "midnight", label: "Midnight", swatchColor: "#40D080", category: "existing", dark: true },
  { name: "obsidian", label: "Obsidian", swatchColor: "#E0A020", category: "existing", dark: true },
  { name: "navy", label: "Navy", swatchColor: "#FF6B4A", category: "existing", dark: true },
  { name: "crimson", label: "Crimson", swatchColor: "#E02020", category: "existing", dark: true },
  { name: "neon-ink", label: "Neon Ink", swatchColor: "#F97316", category: "palette", dark: true },
  { name: "midnight-ocean", label: "Midnight Ocean", swatchColor: "#38BDF8", category: "palette", dark: true },
  { name: "dracula", label: "Dracula", swatchColor: "#BD93F9", category: "palette", dark: true },
  { name: "nord", label: "Nord", swatchColor: "#81A1C1", category: "palette", dark: true },
  { name: "monokai", label: "Monokai", swatchColor: "#A6E22E", category: "palette", dark: true },
  { name: "one-dark", label: "One Dark", swatchColor: "#61AFEF", category: "palette", dark: true },
  { name: "catppuccin-mocha", label: "Catppuccin Mocha", swatchColor: "#CBA6F7", category: "palette", dark: true },
]

export const seasonalThemes: ThemeDefinition[] = [
  { name: "christmas", label: "Christmas Days", swatchColor: "#C8900C", category: "seasonal", dark: true, effect: "snow" },
  { name: "st-patricks", label: "St. Patrick's", swatchColor: "#1A8A2A", category: "seasonal", dark: false, effect: "clovers" },
  { name: "halloween", label: "Halloween", swatchColor: "#E05800", category: "seasonal", dark: true, effect: "skulls" },
  { name: "valentines", label: "Valentine's", swatchColor: "#D01840", category: "seasonal", dark: false, effect: "hearts" },
]

export const allThemes = [...lightThemes, ...darkThemes, ...seasonalThemes]

// A seasonal theme is a valid lightTheme/darkTheme pick like any other — ThemeContext's system-mode
// resolution and the switcher's "which setter do I call" logic both need light/dark partitions that
// include the seasonal ones alongside the regular themes.
export const allLightThemes = [...lightThemes, ...seasonalThemes.filter((theme) => !theme.dark)]
export const allDarkThemes = [...darkThemes, ...seasonalThemes.filter((theme) => theme.dark)]

export function findTheme(name: string): ThemeDefinition | undefined {
  return allThemes.find((theme) => theme.name === name)
}

export type ThemeMode = "light" | "dark" | "system"

export type FontScale = "small" | "medium" | "large" | "xlarge"

export const FONT_SCALE_VALUES: Record<FontScale, number> = {
  small: 1,
  medium: 1.125,
  large: 1.25,
  xlarge: 1.375,
}

export type ContrastMode = "normal" | "medium" | "high"
