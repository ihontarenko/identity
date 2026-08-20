import { cn } from "@/lib/helpers"
import { accountInitials, accountName } from "@/lib/accountDisplay"

interface AccountChipProperties {
  account?: {
    displayName?: string | null
    email?: string | null
  } | null
  subtitle?: string | null
  className?: string
}

/**
 * Initials + name (+ optional subtitle) — the one way a person is rendered on Identity's screens.
 *
 * ⚠️ **Tessera's `MemberChip` with the picture taken out, deliberately.** Same geometry, same two-line
 * shape, same typography, so the two access screens read identically; what is missing is missing because
 * Identity's access payload carries no avatar at all. Adding one is a change to `AccountReference` and
 * to whatever renders the face — not a second chip.
 */
export function AccountChip({ account, subtitle, className }: AccountChipProperties) {
  return (
    <div className={cn("flex min-w-0 items-center gap-2", className)}>
      <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-muted text-[11px] font-medium text-muted-foreground">
        {accountInitials(account)}
      </span>
      <div className="min-w-0">
        <div className="truncate text-sm font-medium">{accountName(account)}</div>
        {subtitle && <div className="truncate text-xs text-muted-foreground">{subtitle}</div>}
      </div>
    </div>
  )
}
