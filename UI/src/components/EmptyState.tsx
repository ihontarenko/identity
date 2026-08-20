import type { LucideIcon } from "lucide-react"
import type { ReactNode } from "react"

interface EmptyStateProperties {
  icon: LucideIcon
  title: string
  message: string
  action?: ReactNode
}

// Ported from Tessera/UI's EmptyState verbatim (itself Innoventa's .emptyPane treatment — icon, title,
// message, centered), so a nothing-here pane reads the same across every product here.
export function EmptyState({ icon: Icon, title, message, action }: EmptyStateProperties) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-2.5 px-6 py-12 text-center text-muted-foreground">
      <Icon className="size-8 opacity-60" />
      <h3 className="text-[15px] font-semibold text-foreground">{title}</h3>
      <p className="max-w-80 text-[13px] leading-relaxed">{message}</p>
      {action}
    </div>
  )
}
