import { LogOut } from "lucide-react"
import { SidebarMenu, SidebarMenuButton, SidebarMenuItem } from "@/components/ui/sidebar"
import { useLogout } from "@/api/authentication"

// Lives in the sidebar footer, matching Central/UI's SignOutButton placement — desktop has no
// persistent app-level header bar to put it in (see ApplicationLayout). Unlike Central/UI's version
// (which signs out of react-oidc-context's upstream provider), Identity manages its own
// session-cookie-based auth directly, so this calls the existing useLogout mutation instead.
export function SignOutButton() {
  const logoutMutation = useLogout()

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <SidebarMenuButton
          disabled={logoutMutation.isPending}
          onClick={() => logoutMutation.mutate()}
          tooltip="Sign out"
        >
          <LogOut className="size-4" />
          <span>Sign out</span>
        </SidebarMenuButton>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
