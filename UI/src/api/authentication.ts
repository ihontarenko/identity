import { useMutation, useQueryClient } from "@tanstack/react-query"
import { httpClient } from "@/api/httpClient"
import { CURRENT_USER_QUERY_KEY } from "@/api/account"

export interface LoginCredentials {
  email: string
  password: string
}

export interface LoginResult {
  redirectTo: string | null
}

export async function login(credentials: LoginCredentials): Promise<LoginResult> {
  const response = await httpClient.post<LoginResult>("/authentication/login", credentials)
  return response.data
}

export async function logout(): Promise<void> {
  await httpClient.post("/authentication/logout")
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: logout,
    onSuccess: () => queryClient.setQueryData(CURRENT_USER_QUERY_KEY, null),
  })
}
