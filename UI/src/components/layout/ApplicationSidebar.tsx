import { NavLink, useLocation } from "react-router-dom"
import { Palette } from "lucide-react"
import { IdentityMark } from "@/components/icons/IdentityMark"
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarGroupContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarFooter,
} from "@/components/ui/sidebar"
import { navigationGroups } from "@/navigation"
import { SignOutButton } from "@/components/layout/SignOutButton"
import { useAuth } from "@/context/AuthContext"

function isNavigationItemActive(pathname: string, itemPath: string) {
  return pathname === itemPath || pathname.startsWith(`${itemPath}/`)
}

// Ported from Central/UI's ApplicationSidebar — same shape (always-expanded desktop sidebar, no
// icon-only collapse, no LanguageSwitcher/i18n since Identity has neither), with the brand block and
// nav destinations adapted to this app.
export function ApplicationSidebar() {
  const { pathname } = useLocation()
  const { can } = useAuth()

  return (
    <Sidebar>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" className="h-auto gap-2.5 px-1.5 py-1" asChild>
              <NavLink to="/">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-primary text-primary-foreground">
                  <IdentityMark className="size-6" />
                </span>
                <span className="flex flex-1 flex-col">
                  <span className="font-display text-[22px] leading-none tracking-[-0.02em]">
                    Identity
                  </span>
                  <span className="mt-[3px] text-[10px] tracking-[0.07em] text-muted-foreground uppercase">
                    identity provider
                  </span>
                </span>
              </NavLink>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        {navigationGroups.map((group) => {
          const items = group.items.filter(
            (item) => !item.requiresPermission || can(item.requiresPermission),
          )

          if (items.length === 0) {
            return null
          }

          return (
            <SidebarGroup key={group.title}>
              <SidebarGroupLabel>{group.title}</SidebarGroupLabel>
              <SidebarGroupContent>
                <SidebarMenu>
                  {items.map((item) => (
                    <SidebarMenuItem key={item.path}>
                      <SidebarMenuButton
                        asChild
                        tooltip={item.title}
                        isActive={isNavigationItemActive(pathname, item.path)}
                      >
                        <NavLink to={item.path}>
                          <item.icon />
                          <span>{item.title}</span>
                        </NavLink>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  ))}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          )
        })}
      </SidebarContent>
      <SidebarFooter>
        {/* A page (AppearanceSettingsPage), not a dropdown — dropdown-based theme pickers break
            under this app's fontScale body.style.zoom mechanism, see AppearanceSettingsPage. */}
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              asChild
              tooltip="Appearance"
              isActive={isNavigationItemActive(pathname, "/settings/appearance")}
            >
              <NavLink to="/settings/appearance">
                <Palette className="size-4" />
                <span>Appearance</span>
              </NavLink>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
        <SignOutButton />
      </SidebarFooter>
    </Sidebar>
  )
}
