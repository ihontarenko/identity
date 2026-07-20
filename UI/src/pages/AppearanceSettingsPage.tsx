import { Check, Contrast, Laptop, Moon, Snowflake, Sun, Type } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Switch } from "@/components/ui/switch"
import { PageHeader } from "@/components/PageHeader"
import { useTheme } from "@/context/ThemeContext"
import {
  darkThemes,
  lightThemes,
  seasonalThemes,
  type ContrastMode,
  type FontScale,
} from "@/theming"

const FONT_SCALE_LABELS: Record<FontScale, string> = {
  small: "Small",
  medium: "Medium",
  large: "Large",
  xlarge: "Extra large",
}

const CONTRAST_MODE_LABELS: Record<ContrastMode, string> = {
  normal: "Normal",
  medium: "Medium",
  high: "High",
}

function ThemeSwatchButton({
  label,
  swatchColor,
  selected,
  onClick,
}: {
  label: string
  swatchColor: string
  selected: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex items-center gap-2 rounded-md border px-3 py-2 text-left text-sm transition-colors hover:bg-accent data-[selected=true]:border-primary data-[selected=true]:bg-accent"
      data-selected={selected}
    >
      <span className="size-3 shrink-0 rounded-full border" style={{ backgroundColor: swatchColor }} />
      <span className="flex-1">{label}</span>
      {selected && <Check className="size-4 shrink-0" />}
    </button>
  )
}

// Ported from Central/UI's AppearanceSettingsPage (itself ported from Moneta/UI's) — a dedicated
// page instead of a sidebar-footer dropdown. The dropdown's content (mode + 27 theme swatches +
// seasonal + text size + contrast) is tall enough that, combined with fontScale's body.style.zoom,
// it triggered a Radix/zoom positioning bug (the trigger's measured position under zoom landed
// beyond the real viewport). A normal page has no viewport-constrained popover positioning to get
// wrong in the first place — this replaces Identity's old ThemeSwitcher dropdown for the same
// reason it was replaced in Moneta and Central.
export function AppearanceSettingsPage() {
  const {
    mode,
    lightTheme,
    darkTheme,
    fontScale,
    contrastMode,
    seasonalEffectEnabled,
    setMode,
    setLightTheme,
    setDarkTheme,
    setFontScale,
    setContrastMode,
    setSeasonalEffectEnabled,
  } = useTheme()

  return (
    <>
      <PageHeader title="Appearance" description="Theme, text size, and contrast" />
      <div className="space-y-4">
        <Card>
          <CardHeader>
            <CardTitle>Mode</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button variant={mode === "light" ? "default" : "outline"} size="sm" onClick={() => setMode("light")}>
              <Sun /> Light
            </Button>
            <Button variant={mode === "dark" ? "default" : "outline"} size="sm" onClick={() => setMode("dark")}>
              <Moon /> Dark
            </Button>
            <Button variant={mode === "system" ? "default" : "outline"} size="sm" onClick={() => setMode("system")}>
              <Laptop /> System
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Light theme</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
            {lightThemes.map((theme) => (
              <ThemeSwatchButton
                key={theme.name}
                label={theme.label}
                swatchColor={theme.swatchColor}
                selected={lightTheme === theme.name}
                onClick={() => setLightTheme(theme.name)}
              />
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Dark theme</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
            {darkThemes.map((theme) => (
              <ThemeSwatchButton
                key={theme.name}
                label={theme.label}
                swatchColor={theme.swatchColor}
                selected={darkTheme === theme.name}
                onClick={() => setDarkTheme(theme.name)}
              />
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Seasonal</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              {seasonalThemes.map((theme) => (
                <ThemeSwatchButton
                  key={theme.name}
                  label={theme.label}
                  swatchColor={theme.swatchColor}
                  selected={(theme.dark ? darkTheme : lightTheme) === theme.name}
                  onClick={() => (theme.dark ? setDarkTheme(theme.name) : setLightTheme(theme.name))}
                />
              ))}
            </div>
            <div className="flex items-center justify-between rounded-md border p-3">
              <span className="flex items-center gap-2 text-sm font-medium">
                <Snowflake className="size-4" />
                Seasonal effects
              </span>
              <Switch checked={seasonalEffectEnabled} onCheckedChange={setSeasonalEffectEnabled} />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Type className="size-4" />
              Text size
            </CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            {(Object.keys(FONT_SCALE_LABELS) as FontScale[]).map((scale) => (
              <Button
                key={scale}
                variant={fontScale === scale ? "default" : "outline"}
                size="sm"
                onClick={() => setFontScale(scale)}
              >
                {FONT_SCALE_LABELS[scale]}
              </Button>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Contrast className="size-4" />
              Contrast
            </CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            {(Object.keys(CONTRAST_MODE_LABELS) as ContrastMode[]).map((contrast) => (
              <Button
                key={contrast}
                variant={contrastMode === contrast ? "default" : "outline"}
                size="sm"
                onClick={() => setContrastMode(contrast)}
              >
                {CONTRAST_MODE_LABELS[contrast]}
              </Button>
            ))}
          </CardContent>
        </Card>
      </div>
    </>
  )
}
