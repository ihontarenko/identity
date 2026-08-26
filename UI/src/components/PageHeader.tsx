import type { ReactNode } from "react"

interface PageHeaderProperties {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
}

// Ported from Central/UI's PageHeader (itself ported from Innoventa's own ui.module.css topbar)
// verbatim: 18px/24px/13px padding, an 18px/600/-0.02em title with a 12px muted subtitle 2px below,
// border-bottom.
//
// The -mx-4 -mt-4 cancels ApplicationLayout's content wrapper's own p-4 (this is always that
// wrapper's first child) so the border-bottom — and the header's own background — reach the true
// left/right/top edges. px-6/pt-[18px]/pb-[13px] then re-establish the inset from those true edges,
// so the title/actions land in the same visual position regardless of the cancel-and-reapply.
export function PageHeader({ title, description, actions }: PageHeaderProperties) {
  return (
    // ⚠️ A narrower inset below `sm`: 24px is right on a desk and is six percent of a phone's width
    // spent on nothing, twice, on the screen where the title has least room to begin with.
    <header className="-mx-4 -mt-4 flex flex-shrink-0 flex-wrap items-end justify-between gap-x-4 gap-y-2 border-b bg-background px-4 pt-[18px] pb-[13px] sm:px-6">
      {/* ⚠️ min-w-0 and truncate, so a long user-typed title gives way instead of pushing the actions
          off the right edge. Every Button here is shrink-0 whitespace-nowrap, so without this the
          actions win the width fight and the primary one — Save, Edit — is the half that leaves. */}
      <div className="min-w-0">
        {typeof title === "string" ? (
          <h1 className="truncate font-display text-lg font-semibold tracking-[-0.02em]">{title}</h1>
        ) : (
          title
        )}
        {description && <div className="mt-0.5 text-xs text-muted-foreground">{description}</div>}
      </div>
      {/* ⚠️ **Dropping to a row of its own is not the same as fitting on it.** `flex-wrap` above puts
          the actions under the title, and they still ran off the right edge, because a wrapped flex
          item is offered the line's width and `shrink-0` refuses it: a `w-64` search field plus a
          Button (each one shrink-0 whitespace-nowrap) is ~400px of refusal on a 375px phone, and there
          is nowhere to scroll to the remainder — the only scroller in the application is vertical,
          inside an overflow-hidden SidebarInset. So: full-width and shrinkable below `sm`, and the
          unshrinkable cluster at the right it has always been from `sm` up.

          ⚠️ The `data-slot=input` rule is what makes that fit look deliberate rather than ragged. Left
          alone flex wraps before it shrinks, so the field keeps its 256px and the primary button drops
          to a third row by itself; `flex-1` re-bases the field on what is actually left over — it
          already carries `min-w-0`. Fields only: a button must never be stretched to fill. */}
      {actions && (
        <div className="flex w-full min-w-0 flex-wrap items-center gap-2 [&>[data-slot=input]]:flex-1 sm:w-auto sm:shrink-0 sm:[&>[data-slot=input]]:flex-none">
          {actions}
        </div>
      )}
    </header>
  )
}
