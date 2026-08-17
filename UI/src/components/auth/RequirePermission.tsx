import type { ReactNode } from "react"
import { Navigate, useLocation } from "react-router-dom"
import { ShieldAlert } from "lucide-react"
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { useAuth } from "@/context/AuthContext"
import type { Permission } from "@/permissions"

interface RequirePermissionProperties {
  permission: Permission
  children: ReactNode
}

/**
 * Replaced `RequireAdministrator`, which asked whether somebody was an administrator — one question
 * standing in for six different powers. A route now names the permission it is actually about.
 *
 * ⚠️ This hides a page; it does not protect one. The backend refuses the same call with the same
 * permission, and that refusal is the enforcement. What this buys is a reader not being sent into a
 * screen whose every request will 403.
 */
export function RequirePermission({ permission, children }: RequirePermissionProperties) {
  const { isAuthenticated, can, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return null
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }

  if (!can(permission)) {
    return (
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <ShieldAlert className="size-5 text-destructive" />
            <CardTitle>You don't have access to this page</CardTitle>
          </div>
          <CardDescription>
            It needs the <code className="font-mono">{permission}</code> permission. Somebody who
            administers access can grant it to you.
          </CardDescription>
        </CardHeader>
      </Card>
    )
  }

  return children
}
