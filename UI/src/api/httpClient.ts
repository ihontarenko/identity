import axios from "axios"
import { queryClient } from "@/lib/queryClient"

// Identity is its own authorization server, not a client of one — the session lives in an
// HttpOnly cookie the browser sends automatically (same-origin in both dev, via vite.config.ts's
// proxy, and production, where Spring Boot serves this SPA's build output itself). CSRF protection
// stays on for that reason (see SecurityConfiguration.defaultSecurityFilterChain); axios's
// xsrfCookieName/xsrfHeaderName defaults already match Spring's CookieCsrfTokenRepository names,
// spelled out here so that alignment isn't accidental.
export const httpClient = axios.create({
  baseURL: "/api",
  withCredentials: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
})

// A session can expire (or an admin's own account can get disabled) mid-visit — without this, every
// page independently mis-described that as "Could not update user."/"Could not save profile." with
// no indication re-authentication is what's actually needed. On any 401 from a call other than the
// auth-check itself, invalidate the ["me"] query (api/account.ts's CURRENT_USER_QUERY_KEY — not
// imported directly, to avoid a circular import with that module's own httpClient import) so
// AuthContext refetches, resolves to unauthenticated, and RequireAuthentication's existing redirect
// takes it from there. "/me" itself is excluded since a 401 on that specific call is the expected,
// already-handled "not logged in yet" case (fetchCurrentUserOrNull), not a session expiring.
httpClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && error.config?.url !== "/me") {
      void queryClient.invalidateQueries({ queryKey: ["me"] })
    }
    return Promise.reject(error)
  },
)
