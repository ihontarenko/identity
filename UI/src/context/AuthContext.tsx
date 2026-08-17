import { createContext, useContext, type ReactNode } from "react"
import { isAxiosError } from "axios"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { CURRENT_USER_QUERY_KEY, fetchCurrentUser, type CurrentUser } from "@/api/account"
import type { Permission } from "@/permissions"

interface AuthContextValue {
  user: CurrentUser | undefined
  isLoading: boolean
  isAuthenticated: boolean
  /**
   * ⚠️ Replaced `isAdmin`. A screen that branches on being an administrator is a screen with one
   * switch behind six different powers — and it breaks outright for somebody granted a permission
   * personally without a role, which is how `user:delete` is meant to be held.
   *
   * Pass a constant from `@/permissions`, never a literal.
   */
  can: (permission: Permission) => boolean
  refresh: () => Promise<unknown>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

async function fetchCurrentUserOrNull(): Promise<CurrentUser | null> {
  try {
    return await fetchCurrentUser()
  } catch (error) {
    if (isAxiosError(error) && error.response?.status === 401) {
      return null
    }
    throw error
  }
}

interface AuthProviderProperties {
  children: ReactNode
}

export function AuthProvider({ children }: AuthProviderProperties) {
  const queryClient = useQueryClient()
  const { data: user, isLoading } = useQuery({
    queryKey: CURRENT_USER_QUERY_KEY,
    queryFn: fetchCurrentUserOrNull,
    staleTime: 60_000,
  })

  const value: AuthContextValue = {
    user: user ?? undefined,
    isLoading,
    isAuthenticated: Boolean(user),
    // Nobody signed in holds nothing, rather than holding everything — the safe direction for a
    // check that runs before /me has answered.
    can: (permission) => Boolean(user?.permissions.includes(permission)),
    refresh: () => queryClient.invalidateQueries({ queryKey: CURRENT_USER_QUERY_KEY }),
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)

  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider")
  }

  return context
}
