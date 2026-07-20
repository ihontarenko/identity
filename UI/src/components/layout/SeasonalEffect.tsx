import type { CSSProperties } from "react"
import { useTheme } from "@/context/ThemeContext"

// Ported from Innoventa/UI/src/components/ui/SeasonalEffect.tsx — same particle set, same
// deterministic-from-index positioning/timing, same "drop" keyframes (see index.css).
const EFFECT_CHARACTERS: Record<string, string[]> = {
  snow: ["❄", "❅", "❆", "·", "·", "❄"],
  hearts: ["♥", "♡", "♥", "❤", "♡"],
  clovers: ["☘", "♣", "☘", "🥴", "🍺"],
  skulls: ["💀", "👻", "💀", "🕷", "💀", "👻"],
}

interface ParticleStyle extends CSSProperties {
  "--drift"?: string
}

export function SeasonalEffect() {
  const { activeEffect, seasonalEffectEnabled } = useTheme()

  if (!activeEffect || !seasonalEffectEnabled) {
    return null
  }

  const characters = EFFECT_CHARACTERS[activeEffect] ?? ["·"]

  return (
    <div className="pointer-events-none fixed inset-0 z-[9998] overflow-hidden" aria-hidden="true">
      {Array.from({ length: 15 }, (_, index) => {
        const style: ParticleStyle = {
          left: `${(index * 6.8) % 100}%`,
          top: "-1.5em",
          color: "var(--particle, var(--accent-foreground))",
          animationName: "drop",
          animationTimingFunction: "linear",
          animationIterationCount: "infinite",
          animationDelay: `${(index * 0.65) % 7}s`,
          animationDuration: `${6 + ((index * 1.1) % 7)}s`,
          fontSize: `${14 + ((index * 3) % 22)}px`,
          opacity: 0.45 + (index % 4) * 0.12,
          "--drift": `${-20 + ((index * 9) % 40)}px`,
        }
        return (
          <span key={index} className="absolute will-change-transform select-none" style={style}>
            {characters[index % characters.length]}
          </span>
        )
      })}
    </div>
  )
}
