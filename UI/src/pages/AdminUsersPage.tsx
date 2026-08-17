import { useState } from "react"
import { RefreshCw, Trash2, UserPlus } from "lucide-react"
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
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  useAdminUsers,
  useCreateUser,
  useDeleteUser,
  useSetUserEnabled,
  type AdminUser,
} from "@/api/adminUsers"
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
  const { can } = useAuth()

  return (
    <>
      <PageHeader title="Users" description="Raise, enable, disable, or delete accounts." />

      {can(PERMISSIONS.CREATE_USER) && <CreateUserForm existing={users ?? []} />}

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

/**
 * Raising an account, and the one warning that matters while doing it.
 *
 * ⚠️ The uniqueness constraint is `(email, provider)`, so an address that already exists as a Google
 * or GitHub identity can still be raised as a local one — legally, and occasionally on purpose. It is
 * not refused; it is *shown*, because two accounts sharing one address is exactly the kind of thing
 * that should be noticed while it is being done rather than discovered months later.
 */
function CreateUserForm({ existing }: { existing: AdminUser[] }) {
  const [email, setEmail] = useState("")
  const [displayName, setDisplayName] = useState("")
  const [initialPassword, setInitialPassword] = useState("")
  const createUserMutation = useCreateUser()

  const clash = existing.find(
    (account) => account.email.toLowerCase() === email.trim().toLowerCase(),
  )

  function submit() {
    createUserMutation.mutate(
      { email: email.trim(), displayName: displayName.trim(), initialPassword },
      {
        onSuccess: (created) => {
          toast.success(`${created.email} raised — they must change the password at first sign-in.`)
          setEmail("")
          setDisplayName("")
          setInitialPassword("")
        },
        onError: (error) => toast.error(extractErrorMessage(error, "Could not raise the account.")),
      },
    )
  }

  return (
    <div className="flex flex-col gap-2 rounded-md border p-3">
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex min-w-52 flex-1 flex-col gap-1.5">
          <Label htmlFor="newEmail">Email</Label>
          <Input
            id="newEmail"
            type="email"
            value={email}
            onChange={(entry) => setEmail(entry.target.value)}
            placeholder="someone@example.com"
          />
        </div>
        <div className="flex min-w-44 flex-1 flex-col gap-1.5">
          <Label htmlFor="newDisplayName">Display name</Label>
          <Input
            id="newDisplayName"
            value={displayName}
            onChange={(entry) => setDisplayName(entry.target.value)}
          />
        </div>
        <div className="flex min-w-52 flex-1 flex-col gap-1.5">
          <Label htmlFor="newPassword">Initial password</Label>
          <Input
            id="newPassword"
            type="password"
            value={initialPassword}
            onChange={(entry) => setInitialPassword(entry.target.value)}
            placeholder="At least 8 characters"
          />
        </div>
        <Button
          disabled={!email.trim() || initialPassword.length < 8 || createUserMutation.isPending}
          onClick={submit}
        >
          <UserPlus className="size-4" />
          Raise account
        </Button>
      </div>

      <p className="text-[11.5px] text-muted-foreground">
        The account is local — a Google or GitHub identity is created by that provider's first sign-in
        and cannot be typed in here. Whoever signs in with this password must replace it before they
        can do anything, here or in any application that signs in through Identity.
      </p>

      {clash && (
        <p className="text-[11.5px] text-amber-600 dark:text-amber-500">
          ⚠️ {clash.email} already exists as a {clash.provider} account. This will raise a second,
          separate account with the same address — Identity does not link them.
        </p>
      )}
    </div>
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
      <TableCell>
        {targetUser.email}
        {targetUser.mustChangePassword && (
          <Badge variant="outline" className="ml-2">
            must change password
          </Badge>
        )}
      </TableCell>
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
