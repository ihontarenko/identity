import type { SVGProps } from "react"

/**
 * Placeholder mark — a plain shield-check outline, deliberately not a designed logo. Mirrors
 * Central/UI's CentralMark (same "unblock scaffolding, defer real branding" precedent — see that
 * component's comment for the reasoning this repeats), shaped for an identity/auth service instead
 * of a generic platform mark.
 */
export function IdentityMark(properties: SVGProps<SVGSVGElement>) {
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
      <path d="M12 3l7 3v6c0 4.5-3 8-7 9-4-1-7-4.5-7-9V6l7-3z" />
      <path d="M9.5 12l1.8 1.8L14.5 10" />
    </svg>
  )
}
