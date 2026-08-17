import { httpClient } from "@/api/httpClient"

export const CURRENT_USER_QUERY_KEY = ["me"]

export interface CurrentUser {
  id: string
  email: string
  displayName: string | null
  avatarUrl: string | null
  provider: string
  /**
   * ⚠️ Replaced `role: "USER" | "ADMIN"`, and it is not a rename. Every control used to be rendered
   * from that one boolean, which stops meaning anything the moment somebody holds a permission
   * personally without holding a role — which is exactly how `user:delete` is held, since it sits in
   * no role at all. Render a control from the permission that backs it, via `useAuth().can(...)`.
   */
  permissions: string[]
}

export interface UpdateProfileRequest {
  displayName: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  const response = await httpClient.get<CurrentUser>("/me")
  return response.data
}

export async function updateProfile(request: UpdateProfileRequest): Promise<CurrentUser> {
  const response = await httpClient.put<CurrentUser>("/account/profile", request)
  return response.data
}

export async function changePassword(request: ChangePasswordRequest): Promise<void> {
  await httpClient.put("/account/password", request)
}
