import type { SVGProps } from "react"

/**
 * Kiwi — pages standing in a section: the family's rounded tile with three leaves shelved inside it.
 * Copied stroke for stroke from `Kiwi/UI/src/components/icons/KiwiMark.tsx` so the product wears the
 * same face here as it does at home.
 *
 * ⚠️ <strong>The outer tile is Tessera's exact geometry</strong>, which is what makes this read as a
 * member of the family and also what makes the two hardest to tell apart at 16&nbsp;px. Chosen
 * knowingly there; repeating the choice here rather than inventing a second Kiwi.
 */
export function KiwiMark(properties: SVGProps<SVGSVGElement>) {
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
      <path d="M8 8v8" />
      <path d="M12 8v8" />
      <path d="M16 8v8" />
    </svg>
  )
}
