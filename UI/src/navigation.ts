import { KeyRound, ShieldCheck, UserRound, type LucideIcon } from "lucide-react"
import { PERMISSIONS, type Permission } from "@/permissions"

export interface NavigationItem {
  title: string
  path: string
  icon: LucideIcon
  /**
   * ⚠️ Replaced `requiresAdmin?: boolean`. A destination is hidden by the permission the page it
   * leads to actually asks for, so a person granted that permission personally — with no role at all
   * — still sees the way in.
   */
  requiresPermission?: Permission
}

export interface NavigationGroup {
  title: string
  items: NavigationItem[]
}

// Identity's actual destinations only — Account for every signed-in user, Users behind the
// permission that screen's own endpoint asks for. Appearance lives directly in
// ApplicationSidebar's footer (same placement as Central/UI's), not in this list, since it isn't a
// gated navigation destination.
export const navigationGroups: NavigationGroup[] = [
  {
    title: "Account",
    items: [
      { title: "Account", path: "/account", icon: UserRound },
      {
        title: "Users",
        path: "/admin/users",
        icon: ShieldCheck,
        requiresPermission: PERMISSIONS.READ_USER,
      },
      {
        title: "Access",
        path: "/admin/access",
        icon: KeyRound,
        requiresPermission: PERMISSIONS.ADMINISTER_ACCESS,
      },
    ],
  },
]
