import { isAxiosError } from "axios"

/** The RFC 7807 `detail` message off a failed request, or a fallback — shown in a toast. */
export function apiErrorMessage(error: unknown, fallback = "Something went wrong"): string {
  if (isAxiosError(error) && typeof error.response?.data?.detail === "string") {
    return error.response.data.detail as string
  }

  return fallback
}
