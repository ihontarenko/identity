import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { httpClient } from "@/api/httpClient"

export interface AdminUser {
  id: string
  email: string
  displayName: string | null
  provider: string
  enabled: boolean
  createdAt: string
}

const ADMIN_USERS_QUERY_KEY = ["admin", "users"]

async function fetchUsers(): Promise<AdminUser[]> {
  const response = await httpClient.get<AdminUser[]>("/admin/users")
  return response.data
}

async function setUserEnabled(id: string, enabled: boolean): Promise<AdminUser> {
  const response = await httpClient.patch<AdminUser>(`/admin/users/${id}/enabled`, { enabled })
  return response.data
}

async function deleteUserById(id: string): Promise<void> {
  await httpClient.delete(`/admin/users/${id}`)
}

export function useAdminUsers() {
  return useQuery({ queryKey: ADMIN_USERS_QUERY_KEY, queryFn: fetchUsers })
}

export function useSetUserEnabled() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) => setUserEnabled(id, enabled),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ADMIN_USERS_QUERY_KEY }),
  })
}

export function useDeleteUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteUserById(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ADMIN_USERS_QUERY_KEY }),
  })
}
