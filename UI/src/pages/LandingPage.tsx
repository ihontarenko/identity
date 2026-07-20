import { Link } from "react-router-dom"
import { ExternalLink, ShieldCheck, UserRound } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { CentralMark } from "@/components/icons/CentralMark"
import { InnoventaMark } from "@/components/icons/InnoventaMark"
import { MonetaMark } from "@/components/icons/MonetaMark"
import { useAuth } from "@/context/AuthContext"
import { useApplicationLinks } from "@/api/applicationLinks"

export function LandingPage() {
  const { user, isAdmin } = useAuth()
  const { data: applicationLinks } = useApplicationLinks()

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-1 flex-col justify-center gap-8 py-12">
      <div className="text-center">
        <h1 className="font-display text-4xl font-bold tracking-tight">Identity</h1>
        <p className="mt-3 text-muted-foreground">
          Centralized sign-in for the Innoventa and Moneta family of applications.
        </p>
        <div className="mt-6 flex justify-center gap-3">
          {user ? (
            <Button asChild>
              <Link to="/account">
                <UserRound className="size-4" />
                Manage your account
              </Link>
            </Button>
          ) : (
            <Button asChild>
              <Link to="/login">
                <UserRound className="size-4" />
                Sign in
              </Link>
            </Button>
          )}
          {isAdmin && (
            <Button variant="outline" asChild>
              <Link to="/admin/users">
                <ShieldCheck className="size-4" />
                Manage users
              </Link>
            </Button>
          )}
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardHeader>
            <span className="flex size-9 items-center justify-center rounded-[10px] bg-primary text-primary-foreground">
              <InnoventaMark className="size-5" />
            </span>
            <CardTitle className="mt-2">Innoventa</CardTitle>
            <CardDescription>Forms, inventory, and entries platform.</CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="outline" asChild>
              <a href={applicationLinks?.innoventaUrl ?? "#"}>
                Open Innoventa
                <ExternalLink className="size-4" />
              </a>
            </Button>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <span className="flex size-9 items-center justify-center rounded-[10px] bg-primary text-primary-foreground">
              <MonetaMark className="size-5" />
            </span>
            <CardTitle className="mt-2">Moneta</CardTitle>
            <CardDescription>Personal finance management platform.</CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="outline" asChild>
              <a href={applicationLinks?.monetaUrl ?? "#"}>
                Open Moneta
                <ExternalLink className="size-4" />
              </a>
            </Button>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <span className="flex size-9 items-center justify-center rounded-[10px] bg-primary text-primary-foreground">
              <CentralMark className="size-5" />
            </span>
            <CardTitle className="mt-2">Central</CardTitle>
            <CardDescription>Shared platform services — translations, AI assistant gateway.</CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="outline" asChild>
              <a href={applicationLinks?.centralUrl ?? "#"}>
                Open Central
                <ExternalLink className="size-4" />
              </a>
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
