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
    <header className="-mx-4 -mt-4 flex flex-shrink-0 items-end justify-between gap-4 border-b bg-background px-6 pt-[18px] pb-[13px]">
      <div>
        {typeof title === "string" ? (
          <h1 className="font-display text-lg font-semibold tracking-[-0.02em]">{title}</h1>
        ) : (
          title
        )}
        {description && <div className="mt-0.5 text-xs text-muted-foreground">{description}</div>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </header>
  )
}
