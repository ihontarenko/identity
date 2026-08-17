import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { httpClient } from "@/api/httpClient"

export interface AdminUser {
  id: string
  email: string
  displayName: string | null
  provider: string
  enabled: boolean
  /** Still holding the password an administrator set — has not been replaced yet. */
  mustChangePassword: boolean
  createdAt: string
}

export interface CreateUserRequest {
  email: string
  displayName: string
  initialPassword: string
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

export function useCreateUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (request: CreateUserRequest) => {
      const response = await httpClient.post<AdminUser>("/admin/users", request)
      return response.data
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ADMIN_USERS_QUERY_KEY }),
  })
}

export function useSetUserPassword() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, newPassword }: { id: string; newPassword: string }) =>
      httpClient.post(`/admin/users/${id}/password`, { newPassword }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ADMIN_USERS_QUERY_KEY }),
  })
}
