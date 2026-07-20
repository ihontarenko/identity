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
              <TableHead>Role</TableHead>
              <TableHead>Enabled</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading && (
              <TableRow>
                <TableCell colSpan={6} className="text-center text-muted-foreground">
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
  const { user: currentUser } = useAuth()
  const setEnabledMutation = useSetUserEnabled()
  const deleteUserMutation = useDeleteUser()
  const isSelf = targetUser.id === currentUser?.id

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
        <Badge variant={targetUser.role === "ADMIN" ? "default" : "outline"}>{targetUser.role}</Badge>
      </TableCell>
      <TableCell>
        <Switch
          checked={targetUser.enabled}
          disabled={isSelf || setEnabledMutation.isPending}
          onCheckedChange={handleToggleEnabled}
        />
      </TableCell>
      <TableCell className="text-right">
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
      </TableCell>
    </TableRow>
  )
}
