import type { SVGProps } from "react"

/** Ascending coins — matches Moneta/UI's own MonetaLogo mark exactly. */
export function MonetaMark(properties: SVGProps<SVGSVGElement>) {
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
      <circle cx="6" cy="17" r="3.2" />
      <circle cx="12.5" cy="13" r="4.2" />
      <circle cx="19" cy="7.5" r="5.2" />
    </svg>
  )
}
