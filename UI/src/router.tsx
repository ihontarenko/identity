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

export function ApplicationRoutes() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
      </Route>
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
