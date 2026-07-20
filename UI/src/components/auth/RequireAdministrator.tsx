import type { ReactNode } from "react"
import { Navigate, useLocation } from "react-router-dom"
import { ShieldAlert } from "lucide-react"
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { useAuth } from "@/context/AuthContext"

interface RequireAdministratorProperties {
  children: ReactNode
}

export function RequireAdministrator({ children }: RequireAdministratorProperties) {
  const { isAuthenticated, isAdmin, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return null
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }

  if (!isAdmin) {
    return (
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <ShieldAlert className="size-5 text-destructive" />
            <CardTitle>You don't have access to this page</CardTitle>
          </div>
          <CardDescription>Only administrators can manage users.</CardDescription>
        </CardHeader>
      </Card>
    )
  }

  return children
}
