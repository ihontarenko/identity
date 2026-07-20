import { createContext, useContext, type ReactNode } from "react"
import { isAxiosError } from "axios"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { CURRENT_USER_QUERY_KEY, fetchCurrentUser, type CurrentUser } from "@/api/account"

interface AuthContextValue {
  user: CurrentUser | undefined
  isLoading: boolean
  isAuthenticated: boolean
  isAdmin: boolean
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
    isAdmin: user?.role === "ADMIN",
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
