import type { ReactNode } from "react"
import { Link } from "react-router-dom"
import { IdentityMark } from "@/components/icons/IdentityMark"
import { SeasonalEffect } from "@/components/layout/SeasonalEffect"

/**
 * The frame every anonymous-entry screen sits in — a port of `Innoventa/FE/src/components/auth/
 * AuthShell.tsx`, not an interpretation of it. The grid, the panel's `p-10`, the `max-w-sm` column at
 * `gap-6` and both radial washes at thirty percent are that file's numbers.
 *
 * ⚠️ <strong>The brand panel is hidden below `lg`, not shrunk.</strong> On a phone it would be a
 * screenful of marketing above the field somebody came here to type in.
 *
 * ⚠️ <strong>This shell replaces the page's layout rather than sitting inside one.</strong> It is
 * `min-h-svh` and paints its own background, so the routes that use it are outside `PublicLayout` —
 * a header bar above it would push the fold and split the panel. It carries `SeasonalEffect` itself
 * for exactly that reason.
 */
export function AuthShell({
  title,
  subtitle,
  promise,
  promises,
  children,
  footer,
}: {
  title: string
  subtitle?: ReactNode
  /** The panel's headline. Two lines, broken by hand — the break is part of the typesetting. */
  promise: ReactNode
  promises: string[]
  children: ReactNode
  /** The way out — "back to Identity", "manage your account". Always last, always centred. */
  footer?: ReactNode
}) {
  return (
    <div className="grid min-h-svh bg-background lg:grid-cols-[1fr_minmax(420px,38%)]">
      <SeasonalEffect />
      <BrandPanel promise={promise} promises={promises} />

      <div className="flex items-center justify-center p-6">
        <div className="flex w-full max-w-sm flex-col gap-6">
          <Link to="/" className="flex items-center gap-2 lg:hidden">
            <span className="flex size-9 items-center justify-center rounded-[12px] bg-primary text-primary-foreground">
              <IdentityMark className="size-6" />
            </span>
            <span className="font-display text-base font-semibold tracking-[-0.02em]">Identity</span>
          </Link>

          <div className="flex flex-col gap-1">
            <h1 className="font-display text-xl font-semibold tracking-[-0.02em]">{title}</h1>
            {subtitle && <p className="text-sm text-muted-foreground">{subtitle}</p>}
          </div>

          {children}

          {footer && <div className="text-center text-sm text-muted-foreground">{footer}</div>}
        </div>
      </div>
    </div>
  )
}

/**
 * ⚠️ <strong>The one deviation from the port, and it is the copy.</strong> Innoventa promises an
 * inventory that takes the shape of what you count, which says nothing whatsoever about a service that
 * mints tokens. The words are Identity's; the layout is not touched by a pixel.
 */
function BrandPanel({ promise, promises }: { promise: ReactNode; promises: string[] }) {
  return (
    <div className="relative hidden flex-col justify-between overflow-hidden bg-primary p-10 text-primary-foreground lg:flex">
      {/* Two soft washes rather than an image: no asset to load, and it takes the palette with it
          through all twenty-seven themes instead of being one fixed blue in every one of them. */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 opacity-30 [background:radial-gradient(60%_50%_at_15%_10%,white,transparent),radial-gradient(50%_40%_at_85%_90%,white,transparent)]"
      />

      <Link to="/" className="relative flex items-center gap-2.5">
        <IdentityMark className="size-9" />
        <span className="font-display text-lg font-semibold tracking-[-0.02em]">Identity</span>
      </Link>

      <div className="relative flex flex-col gap-6">
        <p className="font-display text-3xl leading-tight font-semibold tracking-[-0.03em]">{promise}</p>

        <ul className="flex flex-col gap-2.5 text-sm opacity-90">
          {promises.map((sentence) => (
            <li key={sentence} className="flex items-start gap-2.5">
              <span aria-hidden="true" className="mt-0.5 shrink-0">
                ✓
              </span>
              {sentence}
            </li>
          ))}
        </ul>
      </div>

      <p className="relative text-xs opacity-70">Identity issues the tokens. Nothing else does.</p>
    </div>
  )
}

/** The one alert shape these screens use — success, failure and plain notice differ only in colour. */
export function AuthNotice({ tone, children }: { tone: "success" | "error" | "info"; children: ReactNode }) {
  const skin = {
    success: "border-success/40 bg-success/10",
    error: "border-destructive/40 bg-destructive/10 text-destructive",
    info: "border-border bg-muted/50",
  }[tone]

  return <div className={`rounded-md border p-3 text-sm ${skin}`}>{children}</div>
}
