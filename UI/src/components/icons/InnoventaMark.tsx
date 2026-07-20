import type { SVGProps } from "react"

/** Ported from Innoventa/UI's own favicon.svg glyph — a funnel stem over three fading chevrons (data flowing in and settling). */
export function InnoventaMark(properties: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 32 32"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      {...properties}
    >
      <line x1="16" y1="5" x2="16" y2="16" />
      <polyline points="4,12 16,16 28,12" />
      <polyline points="4,17 16,21 28,17" opacity="0.8" />
      <polyline points="4,22 16,26 28,22" opacity="0.5" />
    </svg>
  )
}
