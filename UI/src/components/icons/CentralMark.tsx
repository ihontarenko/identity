import type { SVGProps } from "react"

/** Mint stamp — matches Central/UI's own CentralMark exactly. */
export function CentralMark(properties: SVGProps<SVGSVGElement>) {
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
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12,8 L12,16 M8,12 L16,12 M9.2,9.2 L14.8,14.8 M14.8,9.2 L9.2,14.8" />
    </svg>
  )
}
