import { Link } from "react-router-dom"
import { LogOut, UserRound } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { AuthShell } from "@/components/auth/AuthShell"
import { useAuth } from "@/context/AuthContext"
import { useLogout } from "@/api/authentication"
import { useApplicationLinks, type ApplicationLinks } from "@/api/applicationLinks"
import { accountInitials, accountName } from "@/lib/accountDisplay"
import { addressHost, PRODUCTS, type ProductDefinition } from "@/products"

/**
 * Where to go next — the same shell the sign-in screen sits in, holding a list instead of a form.
 * Variant 02 of `IDENTITY-DESIGN-AUTHSHELL.html`, whose card is `HubPage.tsx`'s `SpaceCard` geometry:
 * `rounded-lg border p-4 hover:bg-accent/50`, mark and name, then a row of small print underneath.
 *
 * ⚠️ <strong>The small print is the address, not a usage history.</strong> The design mock shows "last
 * opened · 2 hours ago" and "3 workspaces", and Identity knows neither — it mints tokens and never
 * hears what a product does with them. The host is the one true thing it has, and a product with no
 * configured address says so rather than offering a door that opens onto nothing.
 */
export function LandingPage() {
  const { user } = useAuth()
  const { data: applicationLinks } = useApplicationLinks()
  const logoutMutation = useLogout()

  return (
    <AuthShell
      title={user ? "Where to?" : "Identity"}
      subtitle={user ? "Everything your account opens." : "One account for every application here."}
      promise={
        user ? (
          <>
            You are in.
            <br />
            Pick where to work.
          </>
        ) : (
          <>
            One account for
            <br />
            everything you build.
          </>
        )
      }
      promises={
        user
          ? [
              "Four applications, none of which asks again",
              `Signed in as ${accountName(user)}`,
              "Leaving one does not sign you out of the rest",
            ]
          : [
              "Central, Innoventa, Kiwi, Tessera — one sign-in",
              "Google, GitHub, or a password of your own",
              "Every connected client revocable from one page",
            ]
      }
    >
      {!user && (
        <Button asChild>
          <Link to="/login">
            <UserRound className="size-4" />
            Sign in
          </Link>
        </Button>
      )}

      <div className="flex flex-col gap-3">
        {PRODUCTS.map((product) => (
          <ProductCard key={product.name} product={product} applicationLinks={applicationLinks} />
        ))}
      </div>

      {user && (
        <div className="flex items-center gap-2.5 border-t pt-4">
          <Link
            to="/account"
            className="flex min-w-0 flex-1 items-center gap-2.5 rounded-md transition-colors hover:text-foreground"
          >
            <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-accent text-[11px] font-semibold text-accent-foreground">
              {accountInitials(user)}
            </span>
            <span className="flex min-w-0 flex-col">
              <span className="truncate text-[13px] font-medium">{accountName(user)}</span>
              <span className="truncate text-xs text-muted-foreground">{user.email}</span>
            </span>
          </Link>
          <Button
            variant="ghost"
            size="icon"
            aria-label="Sign out"
            disabled={logoutMutation.isPending}
            onClick={() => logoutMutation.mutate()}
          >
            <LogOut className="size-4" />
          </Button>
        </div>
      )}
    </AuthShell>
  )
}

function ProductCard({
  product,
  applicationLinks,
}: {
  product: ProductDefinition
  applicationLinks: ApplicationLinks | undefined
}) {
  const address = applicationLinks?.[product.addressKey] ?? null
  const host = addressHost(address)
  const Mark = product.mark

  const face = (
    <>
      <div className="flex items-start gap-2.5">
        <span aria-hidden="true" className="shrink-0" style={{ color: product.accentColor }}>
          <Mark className="size-[18px]" />
        </span>
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="truncate text-sm font-medium">{product.name}</span>
          <span className="truncate text-xs text-muted-foreground">{product.summary}</span>
        </span>
      </div>

      <div className="mt-auto flex flex-wrap items-center gap-1.5 pt-1">
        {host ? (
          <span className="text-[11px] text-muted-foreground">{host}</span>
        ) : (
          <Badge variant="outline" className="text-[10px]">
            not configured
          </Badge>
        )}
      </div>
    </>
  )

  if (!address) {
    return (
      <div
        className="flex cursor-not-allowed flex-col gap-2 rounded-lg border p-4 opacity-60"
        title={`${product.name} has no address registered under identity.clients.`}
      >
        {face}
      </div>
    )
  }

  return (
    <a
      href={address}
      className="flex flex-col gap-2 rounded-lg border p-4 transition-colors hover:bg-accent/50"
    >
      {face}
    </a>
  )
}
