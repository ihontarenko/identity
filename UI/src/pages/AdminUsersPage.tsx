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
import { useAccessOverview } from "@/api/access"
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

  // ⚠️ Driven by what actually arrived, not by the caller's permission. The server sends holdings
  // only to somebody entitled to see them, so an absent column means "not yours to see" and an
  // empty one would mean "nobody holds anything" — two different statements, and rendering the
  // second when the first is true is the disclosure question answered backwards.
  const showsRoles = (users ?? []).some((account) => account.roles.length > 0)

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
              {/* ⚠️ Roles only, and only for a caller entitled to see them. Everything finer —
                  personal allows and denies, what each role carries — stays on the access screen;
                  answering all of it here would be a second, worse copy of it. */}
              {showsRoles && <TableHead>Roles</TableHead>}
              <TableHead>Enabled</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading && (
              <TableRow>
                <TableCell colSpan={showsRoles ? 6 : 5} className="text-center text-muted-foreground">
                  Loading…
                </TableCell>
              </TableRow>
            )}
            {users?.map((targetUser) => (
              <AdminUserRow key={targetUser.id} targetUser={targetUser} showsRoles={showsRoles} />
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
  const { can } = useAuth()
  const [email, setEmail] = useState("")
  const [displayName, setDisplayName] = useState("")
  const [initialPassword, setInitialPassword] = useState("")
  const [roles, setRoles] = useState<string[]>([])
  const createUserMutation = useCreateUser()

  // ⚠️ The same catalogue the access screen reads, not a second list. A role picker built from
  // constants here would be one release behind the day somebody adds a role.
  const mayHandOutRoles = can(PERMISSIONS.ADMINISTER_ACCESS)
  const { data: access } = useAccessOverview({ enabled: mayHandOutRoles })

  const clash = existing.find(
    (account) => account.email.toLowerCase() === email.trim().toLowerCase(),
  )

  function toggleRole(role: string, on: boolean) {
    setRoles((current) => (on ? [...current, role] : current.filter((held) => held !== role)))
  }

  function submit() {
    createUserMutation.mutate(
      { email: email.trim(), displayName: displayName.trim(), initialPassword, roles },
      {
        onSuccess: (created) => {
          toast.success(`${created.email} raised — they must change the password at first sign-in.`)
          setEmail("")
          setDisplayName("")
          setInitialPassword("")
          setRoles([])
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

      {mayHandOutRoles && (access?.roles.length ?? 0) > 0 && (
        <div className="flex flex-wrap items-center gap-4 border-t pt-3">
          <span className="text-[11.5px] text-muted-foreground">Holds from the start</span>
          {access?.roles.map((role) => (
            <label key={role.name} className="flex items-center gap-2">
              <Switch
                checked={roles.includes(role.name)}
                onCheckedChange={(on) => toggleRole(role.name, on)}
              />
              <span className="font-mono text-[12.5px]">{role.name}</span>
            </label>
          ))}
        </div>
      )}

      {/* ⚠️ Roles only — no personal allow or deny here. Keeping `user:delete` out of the create form
          is what stops it becoming the shortest path to the most dangerous permission in the
          installation; it is granted by name on the access screen, deliberately. */}

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
function AdminUserRow({
  targetUser,
  showsRoles,
}: {
  targetUser: AdminUser
  showsRoles: boolean
}) {
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
      {showsRoles && (
        <TableCell>
          {targetUser.roles.length === 0 && <span className="text-muted-foreground">—</span>}
          <span className="flex flex-wrap gap-1">
            {targetUser.roles.map((role) => (
              <Badge key={role} variant="outline" className="font-mono">
                {role}
              </Badge>
            ))}
          </span>
        </TableCell>
      )}
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
