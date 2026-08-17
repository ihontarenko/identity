import { RefreshCw, Trash2 } from "lucide-react"
import { toast } from "sonner"
import { isAxiosError } from "axios"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import { Switch } from "@/components/ui/switch"
import { Button } from "@/components/ui/button"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import { PageHeader } from "@/components/PageHeader"
import { useAdminUsers, useDeleteUser, useSetUserEnabled, type AdminUser } from "@/api/adminUsers"
import { useAuth } from "@/context/AuthContext"
import { PERMISSIONS } from "@/permissions"

function extractErrorMessage(error: unknown, fallback: string) {
  if (isAxiosError(error) && typeof error.response?.data?.detail === "string") {
    return error.response.data.detail as string
  }
  return fallback
}

export function AdminUsersPage() {
  const { data: users, isLoading, isError, error, refetch } = useAdminUsers()

  return (
    <>
      <PageHeader title="Users" description="Enable, disable, or delete accounts." />

      <div>
        <h2 className="text-[13px] font-semibold">All users</h2>
        <p className="text-[11.5px] text-muted-foreground">
          {isError ? "Could not load" : `${users?.length ?? 0} accounts`}
        </p>
      </div>

      {isError && (
        <div className="flex items-center justify-between gap-3 rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2 text-[13px]">
          <span className="text-destructive">{extractErrorMessage(error, "Could not load users.")}</span>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            <RefreshCw className="size-3.5" />
            Retry
          </Button>
        </div>
      )}

      {!isError && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Email</TableHead>
              <TableHead>Display name</TableHead>
              <TableHead>Provider</TableHead>
              {/* ⚠️ The Role column is gone with the role itself. What somebody holds is roles plus
                  personal grants together, which is the access screen's whole subject — and putting
                  half of it in a badge here would need this endpoint to disclose everybody's grants
                  to anybody who may merely read the register. */}
              <TableHead>Enabled</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading && (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  Loading…
                </TableCell>
              </TableRow>
            )}
            {users?.map((targetUser) => (
              <AdminUserRow key={targetUser.id} targetUser={targetUser} />
            ))}
          </TableBody>
        </Table>
      )}
    </>
  )
}

// Each row owns its own mutation instances so toggling one user's Enabled switch (or deleting one
// user) doesn't disable every other row in the table for the duration of that request — a single
// page-level useSetUserEnabled() shared across every row meant its isPending was one flag for the
// whole table, with no way to tell which row's request was actually in flight.
function AdminUserRow({ targetUser }: { targetUser: AdminUser }) {
  const { user: currentUser, can } = useAuth()
  const setEnabledMutation = useSetUserEnabled()
  const deleteUserMutation = useDeleteUser()
  const isSelf = targetUser.id === currentUser?.id

  // ⚠️ Two permissions, not one. Reading this register no longer implies acting on it — somebody may
  // hold `user:read` and neither of these, and `user:delete` is carried by no role at all, so it is
  // normal rather than exceptional for the delete control to be absent.
  const canDisable = can(PERMISSIONS.DISABLE_USER)
  const canDelete = can(PERMISSIONS.DELETE_USER)

  function handleToggleEnabled(enabled: boolean) {
    setEnabledMutation.mutate(
      { id: targetUser.id, enabled },
      { onError: (error) => toast.error(extractErrorMessage(error, "Could not update user.")) },
    )
  }

  function handleDelete() {
    deleteUserMutation.mutate(targetUser.id, {
      onSuccess: () => toast.success(`${targetUser.email} deleted.`),
      onError: (error) => toast.error(extractErrorMessage(error, "Could not delete user.")),
    })
  }

  return (
    <TableRow>
      <TableCell>{targetUser.email}</TableCell>
      <TableCell>{targetUser.displayName ?? "—"}</TableCell>
      <TableCell>
        <Badge variant="secondary">{targetUser.provider}</Badge>
      </TableCell>
      <TableCell>
        <Switch
          checked={targetUser.enabled}
          disabled={isSelf || !canDisable || setEnabledMutation.isPending}
          onCheckedChange={handleToggleEnabled}
        />
      </TableCell>
      <TableCell className="text-right">
        {!canDelete && <span className="text-[11.5px] text-muted-foreground">—</span>}
        {canDelete && (
        <AlertDialog>
          <AlertDialogTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              disabled={isSelf || deleteUserMutation.isPending}
              aria-label="Delete user"
            >
              <Trash2 className="size-4 text-destructive" />
            </Button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Delete {targetUser.email}?</AlertDialogTitle>
              <AlertDialogDescription>
                This permanently removes the account. This action cannot be undone.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction onClick={handleDelete}>Delete</AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
        )}
      </TableCell>
    </TableRow>
  )
}
