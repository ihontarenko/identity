import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { httpClient } from "@/api/httpClient"
import { CURRENT_USER_QUERY_KEY } from "@/api/account"

export interface PermissionView {
  name: string
  description: string
}

export interface BundleEntryView {
  permission: string
  carriedAt: string
}

export interface RoleView {
  name: string
  assignableAt: string
  /**
   * Whether `policy/identity.jmp` declares this role. ⚠️ The screen must say so: a bundle edited here
   * is rewritten from the document the next time the document changes, so an edit to a declared role
   * survives until somebody changes the file — and then does not.
   */
  declared: boolean
  bundle: BundleEntryView[]
}

export interface AccountRef {
  id: string
  email: string
  displayName: string | null
}

export interface RoleHoldingView {
  /** ⚠️ null where the grant outlived the account — a library table cannot foreign-key into ours. */
  account: AccountRef | null
  roleName: string
  scopeType: string
  source: string
  since: string
}

export interface DirectHoldingView {
  account: AccountRef | null
  permission: string
  allowed: boolean
  scopeType: string
  reason: string
  since: string
}

export interface AccessOverview {
  permissions: PermissionView[]
  roles: RoleView[]
  roleHoldings: RoleHoldingView[]
  directHoldings: DirectHoldingView[]
  accounts: AccountRef[]
}

const ACCESS_QUERY_KEY = ["admin", "access"]

async function fetchAccessOverview(): Promise<AccessOverview> {
  const response = await httpClient.get<AccessOverview>("/admin/access")
  return response.data
}

export function useAccessOverview() {
  return useQuery({ queryKey: ACCESS_QUERY_KEY, queryFn: fetchAccessOverview })
}

/**
 * Every write invalidates the overview **and** `/me`.
 *
 * ⚠️ The second one is not housekeeping: an administrator editing their own holdings changes what the
 * interface may render for them, and without it the sidebar keeps offering a screen the next request
 * would refuse.
 */
function useAccessMutation<TVariables>(request: (variables: TVariables) => Promise<unknown>) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: request,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ACCESS_QUERY_KEY })
      void queryClient.invalidateQueries({ queryKey: CURRENT_USER_QUERY_KEY })
    },
  })
}

export function useSetRoleBundle() {
  return useAccessMutation(({ roleName, bundle }: { roleName: string; bundle: BundleEntryView[] }) =>
    httpClient.put(`/admin/access/roles/${encodeURIComponent(roleName)}/bundle`, { bundle }),
  )
}

export function useAssignRole() {
  return useAccessMutation(({ accountId, roleName }: { accountId: string; roleName: string }) =>
    httpClient.post("/admin/access/assignments", { accountId, roleName }),
  )
}

export function useUnassignRole() {
  return useAccessMutation(({ accountId, roleName }: { accountId: string; roleName: string }) =>
    // ⚠️ A DELETE with a body, so axios needs `{ data }` — passing the object directly sends it as
    // query configuration and the server receives nothing.
    httpClient.delete("/admin/access/assignments", { data: { accountId, roleName } }),
  )
}

export interface GrantRequest {
  accountId: string
  permission: string
  allowed: boolean
  reason: string
}

export function useGrantPermission() {
  return useAccessMutation((request: GrantRequest) =>
    httpClient.post("/admin/access/grants", request),
  )
}

export function useUngrantPermission() {
  return useAccessMutation(({ accountId, permission }: { accountId: string; permission: string }) =>
    httpClient.delete("/admin/access/grants", { data: { accountId, permission } }),
  )
}

/** How a person is named on this screen — the email, since a display name is optional. */
export function nameOf(account: AccountRef | null): string {
  if (!account) {
    return "(account no longer exists)"
  }

  return account.displayName ? `${account.displayName} · ${account.email}` : account.email
}
