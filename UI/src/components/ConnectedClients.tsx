import { toast } from "sonner"
import { isAxiosError } from "axios"
import { Plug, Unplug } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { useConnections, useDisconnect } from "@/api/connections"

function extractErrorMessage(error: unknown, fallback: string) {
  if (isAxiosError(error) && typeof error.response?.data?.detail === "string") {
    return error.response.data.detail as string
  }
  return fallback
}

function readable(moment: string | null) {
  if (!moment) {
    return "never"
  }

  return new Date(moment).toLocaleString()
}

/**
 * The clients connected to Identity's Model Context Protocol endpoint, and the switch that ends one.
 *
 * ⚠️ Rendered even when the list is empty, deliberately. A person who has never connected one should
 * still be able to find out that this is a thing accounts have — and, more to the point, somebody
 * checking whether anything is attached to their account needs the answer "nothing is", not a section
 * that is missing for two different reasons.
 */
export function ConnectedClients() {
  const { data: connections, isLoading } = useConnections()
  const disconnectMutation = useDisconnect()

  // ⚠️ null means the protocol is switched off in this installation — the section is not rendered at
  // all, rather than rendered empty. "Nothing is connected" and "connecting is not available here" are
  // different sentences, and showing the first when the second is true is a small lie on a security
  // screen.
  if (connections === null) {
    return null
  }

  const live = (connections ?? []).filter((connection) => !connection.revoked)
  const ended = (connections ?? []).filter((connection) => connection.revoked)

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Plug className="size-4" />
          <CardTitle>Connected clients</CardTitle>
        </div>
        <CardDescription>
          Programs you have authorized to act as you through Identity's tool endpoint. Each holds
          exactly what you hold — nothing it does is something you could not do yourself. Ending one
          takes effect on its next call.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-2">
        {isLoading && <p className="text-[12.5px] text-muted-foreground">Loading…</p>}

        {!isLoading && live.length === 0 && (
          <p className="text-[12.5px] text-muted-foreground">Nothing is connected to this account.</p>
        )}

        {live.map((connection) => (
          <div
            key={connection.id}
            className="flex flex-wrap items-center justify-between gap-3 rounded-md border px-3 py-2"
          >
            <div>
              <div className="text-[13px]">{connection.clientName ?? "An unnamed client"}</div>
              <div className="text-[11.5px] text-muted-foreground">
                Connected {readable(connection.connectedAt)} · last used {readable(connection.lastUsedAt)}
              </div>
            </div>
            <Button
              variant="outline"
              size="sm"
              disabled={disconnectMutation.isPending}
              onClick={() =>
                disconnectMutation.mutate(connection.id, {
                  onSuccess: () => toast.success("Disconnected."),
                  onError: (error) =>
                    toast.error(extractErrorMessage(error, "Could not disconnect it.")),
                })
              }
            >
              <Unplug className="size-3.5" />
              Disconnect
            </Button>
          </div>
        ))}

        {ended.length > 0 && (
          <p className="text-[11.5px] text-muted-foreground">
            {ended.length} ended {ended.length === 1 ? "connection" : "connections"}{" "}
            <Badge variant="outline">kept for the record</Badge>
          </p>
        )}
      </CardContent>
    </Card>
  )
}
