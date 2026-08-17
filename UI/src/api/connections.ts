import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
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

export function useConnections() {
  return useQuery({
    queryKey: CONNECTIONS_QUERY_KEY,
    queryFn: async () => {
      const response = await httpClient.get<McpConnection[]>("/account/connections")
      return response.data
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
