import { Route, Routes } from "react-router-dom"
import { ApplicationLayout } from "@/components/layout/ApplicationLayout"
import { PublicLayout } from "@/components/layout/PublicLayout"
import { RequireAuthentication } from "@/components/auth/RequireAuthentication"
import { RequireAdministrator } from "@/components/auth/RequireAdministrator"
import { LandingPage } from "@/pages/LandingPage"
import { LoginPage } from "@/pages/LoginPage"
import { AccountPage } from "@/pages/AccountPage"
import { AdminUsersPage } from "@/pages/AdminUsersPage"
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
            <RequireAdministrator>
              <AdminUsersPage />
            </RequireAdministrator>
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
