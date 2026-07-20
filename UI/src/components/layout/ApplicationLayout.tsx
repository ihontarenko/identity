import { Outlet } from "react-router-dom"
import { SidebarProvider, SidebarInset, SidebarTrigger } from "@/components/ui/sidebar"
import { ApplicationSidebar } from "@/components/layout/ApplicationSidebar"
import { SeasonalEffect } from "@/components/layout/SeasonalEffect"

// Sidebar shell for Identity's authenticated pages (/account, /admin/users,
// /settings/appearance) — ported from Central/UI's ApplicationLayout. Public, anonymous-entry-point
// pages (/, /login) deliberately stay outside this shell — see PublicLayout.
//
// No persistent desktop header — sign-out and appearance live in the sidebar footer, and each page's
// own PageHeader carries the title, so content starts flush at the top. The trigger below is
// mobile-only (md:hidden), the only place a header bar exists in this layout at all.
export function ApplicationLayout() {
  return (
    <SidebarProvider>
      <SeasonalEffect />
      <ApplicationSidebar />
      <SidebarInset>
        <div className="flex h-10 shrink-0 items-center border-b px-4 md:hidden">
          <SidebarTrigger className="-ml-1" />
        </div>
        <div className="flex flex-1 flex-col gap-4 p-4">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
