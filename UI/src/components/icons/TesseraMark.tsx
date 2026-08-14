import type { SVGProps } from "react"

/**
 * Tessera — a mosaic tile / stamped token: a rounded square tile with an inset diamond, the
 * "one ticket, one tile" reading (a tessera was a token used as a ticket in Rome). Matches the mark
 * Tessera/UI uses for its own sidebar brand.
 */
export function TesseraMark(properties: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      {...properties}
    >
      <rect x="3.5" y="3.5" width="17" height="17" rx="3.5" />
      <rect x="8.5" y="8.5" width="7" height="7" rx="1.5" transform="rotate(45 12 12)" />
    </svg>
  )
}
