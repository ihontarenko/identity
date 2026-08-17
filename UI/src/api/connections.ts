import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { isAxiosError } from "axios"
import { httpClient } from "@/api/httpClient"

export interface McpConnection {
  id: string
  /** ⚠️ What the client called itself — a claim, shown so a person recognises it, never an identity. */
  clientName: string | null
  connectedAt: string
  lastUsedAt: string | null
  revoked: boolean
}

const CONNECTIONS_QUERY_KEY = ["account", "connections"]

/**
 * ⚠️ `null` means the protocol is switched off in this installation, which is **not** an error.
 *
 * `identity.mcp.signing-secret` being unset unmaps the whole feature, endpoint and routes included, so
 * this call 404s. Letting that surface as a failed query would put a red error box on the account page
 * of every developer who never turned the protocol on. Null instead, and the section renders nothing.
 *
 * Any other failure is still a failure and still throws.
 */
export function useConnections() {
  return useQuery({
    queryKey: CONNECTIONS_QUERY_KEY,
    queryFn: async (): Promise<McpConnection[] | null> => {
      try {
        const response = await httpClient.get<McpConnection[]>("/account/connections")
        return response.data
      } catch (error) {
        if (isAxiosError(error) && error.response?.status === 404) {
          return null
        }
        throw error
      }
    },
  })
}

export function useDisconnect() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => httpClient.delete(`/account/connections/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CONNECTIONS_QUERY_KEY }),
  })
}
