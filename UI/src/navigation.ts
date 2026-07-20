import { ShieldCheck, UserRound, type LucideIcon } from "lucide-react"

export interface NavigationItem {
  title: string
  path: string
  icon: LucideIcon
  requiresAdmin?: boolean
}

export interface NavigationGroup {
  title: string
  items: NavigationItem[]
}

// Identity's actual destinations only — Account for every signed-in user, Users gated to admins.
// Appearance lives directly in ApplicationSidebar's footer (same placement as Central/UI's), not
// in this list, since it isn't a per-role navigation destination.
export const navigationGroups: NavigationGroup[] = [
  {
    title: "Account",
    items: [
      { title: "Account", path: "/account", icon: UserRound },
      { title: "Users", path: "/admin/users", icon: ShieldCheck, requiresAdmin: true },
    ],
  },
]
