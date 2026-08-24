import { Route, Routes } from "react-router-dom"
import { ApplicationLayout } from "@/components/layout/ApplicationLayout"
import { PublicLayout } from "@/components/layout/PublicLayout"
import { RequireAuthentication } from "@/components/auth/RequireAuthentication"
import { RequirePermission } from "@/components/auth/RequirePermission"
import { PERMISSIONS } from "@/permissions"
import { LandingPage } from "@/pages/LandingPage"
import { LoginPage } from "@/pages/LoginPage"
import { AccountPage } from "@/pages/AccountPage"
import { AdminUsersPage } from "@/pages/AdminUsersPage"
import { AccessSettingsPage } from "@/pages/AccessSettingsPage"
import { AppearanceSettingsPage } from "@/pages/AppearanceSettingsPage"
import { ForcePasswordChangePage } from "@/pages/ForcePasswordChangePage"
import { useAuth } from "@/context/AuthContext"

export function ApplicationRoutes() {
  const { user } = useAuth()

  // ⚠️ Every route replaced, not one route added. An account holding a password its administrator
  // chose has nowhere useful to be: the server refuses each of these calls anyway, so routing to them
  // would show a person a sequence of failures instead of the one thing they can do. The server is
  // what enforces it; this only stops the wandering.
  if (user?.mustChangePassword) {
    return (
      <Routes>
        <Route element={<PublicLayout />}>
          <Route path="*" element={<ForcePasswordChangePage />} />
        </Route>
      </Routes>
    )
  }

  return (
    <Routes>
      {/* ⚠️ Outside PublicLayout, and that is the point of the port. AuthShell is `min-h-svh` and paints
          its own background — a header bar above it would push the fold and cut the brand panel in half. */}
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ApplicationLayout />}>
        <Route
          path="/account"
          element={
            <RequireAuthentication>
              <AccountPage />
            </RequireAuthentication>
          }
        />
        <Route
          path="/admin/users"
          element={
            <RequirePermission permission={PERMISSIONS.READ_USER}>
              <AdminUsersPage />
            </RequirePermission>
          }
        />
        <Route
          path="/admin/access"
          element={
            <RequirePermission permission={PERMISSIONS.ADMINISTER_ACCESS}>
              <AccessSettingsPage />
            </RequirePermission>
          }
        />
        <Route
          path="/settings/appearance"
          element={
            <RequireAuthentication>
              <AppearanceSettingsPage />
            </RequireAuthentication>
          }
        />
      </Route>
    </Routes>
  )
}
