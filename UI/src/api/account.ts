import { httpClient } from "@/api/httpClient"

export const CURRENT_USER_QUERY_KEY = ["me"]

export interface CurrentUser {
  id: string
  email: string
  displayName: string | null
  avatarUrl: string | null
  provider: string
  role: "USER" | "ADMIN"
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
