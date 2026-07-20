import { Link, Outlet } from "react-router-dom"
import { UserRound } from "lucide-react"
import { Button } from "@/components/ui/button"
import { SeasonalEffect } from "@/components/layout/SeasonalEffect"
import { useAuth } from "@/context/AuthContext"

// Minimal flat-header shell for the two anonymous-entry-point pages (/ and /login) — a login screen
// or a public landing hub doesn't belong inside the authenticated app's sidebar (see
// ApplicationLayout for that shell instead). Kept close to Identity's previous single-layout header
// bar, just trimmed to what a public page actually needs (brand + one sign-in/account link) — no
// nav links to authenticated-only pages, no appearance switcher.
export function PublicLayout() {
  const { user } = useAuth()

  return (
    <div className="flex min-h-screen flex-col">
      <SeasonalEffect />
      <header className="flex h-16 shrink-0 items-center justify-between gap-2 border-b px-6">
        <Link to="/" className="flex items-center gap-2">
          <span className="flex size-8 items-center justify-center rounded-md bg-primary font-display font-bold text-primary-foreground">
            I
          </span>
          <span className="font-display text-lg font-bold tracking-tight">Identity</span>
        </Link>
        <Button asChild size="sm" variant={user ? "ghost" : "default"}>
          <Link to={user ? "/account" : "/login"}>
            <UserRound className="size-4" />
            {user ? "Account" : "Sign in"}
          </Link>
        </Button>
      </header>
      <main className="flex flex-1 flex-col p-6">
        <Outlet />
      </main>
    </div>
  )
}
