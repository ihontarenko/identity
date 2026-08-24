import type { ComponentType, SVGProps } from "react"
import { CentralMark } from "@/components/icons/CentralMark"
import { InnoventaMark } from "@/components/icons/InnoventaMark"
import { KiwiMark } from "@/components/icons/KiwiMark"
import { TesseraMark } from "@/components/icons/TesseraMark"
import type { ApplicationLinks } from "@/api/applicationLinks"

export interface ProductDefinition {
  /** Which address on `/api/application-links` opens it. */
  addressKey: keyof ApplicationLinks
  name: string
  summary: string
  /**
   * ⚠️ <strong>A fixed hex, not a palette token.</strong> A product does not change colour because the
   * person reading the list picked a different theme — the mark is how they recognise it across all
   * twenty-seven of them. Values are the ones the design file fixed.
   */
  accentColor: string
  mark: ComponentType<SVGProps<SVGSVGElement>>
}

/**
 * The applications this account opens, in the order they are offered.
 *
 * ⚠️ <strong>Four, and the card layout is why the number matters.</strong> `IDENTITY-DESIGN-AUTHSHELL.html`
 * offers this shape and a dense-row one, and picks rows the moment there are more than four cards to
 * fit beside a sign-in form. A fifth entry here is one object; it is also the point to go read that
 * file again rather than squeeze.
 */
export const PRODUCTS: ProductDefinition[] = [
  {
    addressKey: "centralUrl",
    name: "Central",
    summary: "Shared translations and the AI gateway",
    accentColor: "#6b3fcc",
    mark: CentralMark,
  },
  {
    addressKey: "innoventaUrl",
    name: "Innoventa",
    summary: "Forms, inventory, entries",
    accentColor: "#1e78a4",
    mark: InnoventaMark,
  },
  {
    addressKey: "kiwiUrl",
    name: "Kiwi",
    summary: "The knowledge base everything files into",
    accentColor: "#6e9b2c",
    mark: KiwiMark,
  },
  {
    addressKey: "tesseraUrl",
    name: "Tessera",
    summary: "Projects, issues, boards and sprints",
    accentColor: "#c05020",
    mark: TesseraMark,
  },
]

/** The host an address points at — the one honest thing this service knows about a product. */
export function addressHost(address: string | null | undefined): string | null {
  if (!address) {
    return null
  }

  try {
    return new URL(address).host
  } catch {
    return address
  }
}
