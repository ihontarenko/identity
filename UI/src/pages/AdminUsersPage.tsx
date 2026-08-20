import { useState } from "react"
import { KeyRound, RefreshCw, ShieldCheck, Trash2, UserPlus } from "lucide-react"
import { Link } from "react-router-dom"
import { toast } from "sonner"
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
  useSetUserPassword,
  type AdminUser,
} from "@/api/adminUsers"
import { useAccessOverview } from "@/api/access"
import { apiErrorMessage } from "@/api/errors"
import { useAuth } from "@/context/AuthContext"
import { PERMISSIONS } from "@/permissions"

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

      <div className="flex items-end justify-between gap-4">
        <div>
          <h2 className="text-[13px] font-semibold">All users</h2>
          <p className="text-[11.5px] text-muted-foreground">
            {isError ? "Could not load" : `${users?.length ?? 0} accounts`}
          </p>
        </div>

        {/* ⚠️ The register answers "who holds which role" and stops there, so this is the way to the
            question it cannot answer: what somebody was given or refused PERSONALLY. It matters more
            than a convenience link — `user:delete` is carried by no role at all, so the installation's
            most dangerous permission only ever exists as a personal grant, and this row is where
            somebody is most likely to start wondering where it came from.

            Rendered from `access:administer` because that is what the destination is gated on. A link
            to a screen the caller will be refused is worse than no link. */}
        {can(PERMISSIONS.ADMINISTER_ACCESS) && (
          <Button asChild variant="outline" size="sm">
            <Link to="/admin/access">
              <ShieldCheck className="size-3.5" />
              Personal grants and what each role carries
            </Link>
          </Button>
        )}
      </div>

      {isError && (
        <div className="flex items-center justify-between gap-3 rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2 text-[13px]">
          <span className="text-destructive">{apiErrorMessage(error, "Could not load users.")}</span>
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

  function toggleRole(role: string, carried: boolean) {
    setRoles((current) => (carried ? [...current, role] : current.filter((held) => held !== role)))
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
        onError: (error) => toast.error(apiErrorMessage(error, "Could not raise the account.")),
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
  const canSetPassword = can(PERMISSIONS.SET_PASSWORD)

  function handleToggleEnabled(enabled: boolean) {
    setEnabledMutation.mutate(
      { id: targetUser.id, enabled },
      { onError: (error) => toast.error(apiErrorMessage(error, "Could not update user.")) },
    )
  }

  function handleDelete() {
    deleteUserMutation.mutate(targetUser.id, {
      // ⚠️ The grants are named because they are the half nobody expects. Deleting an account also takes
      // back every role and personal grant it held, and there is nowhere else that is reported — this
      // service keeps no audit table, and the rows are gone by the time anybody could go and look.
      onSuccess: ({ revokedGrants }) =>
        toast.success(
          revokedGrants === 0
            ? `${targetUser.email} deleted — they held nothing.`
            : `${targetUser.email} deleted, along with ${revokedGrants} ${
                revokedGrants === 1 ? "grant" : "grants"
              } they held.`,
        ),
      onError: (error) => toast.error(apiErrorMessage(error, "Could not delete user.")),
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
        {/* ⚠️ The dash means "nothing on this row is yours to do", so it appears only when BOTH controls
            are absent. A dash beside a live button reads as a broken cell rather than as an absence. */}
        {!canSetPassword && !canDelete && (
          <span className="text-[11.5px] text-muted-foreground">—</span>
        )}
        <span className="inline-flex items-center justify-end gap-1">
        {canSetPassword && <SetPasswordButton targetUser={targetUser} isSelf={isSelf} />}
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
              {/* ⚠️ Said before, not after. The second sentence is the part somebody does not expect,
                  and a warning that arrives in the toast is a warning that arrives too late. */}
              <AlertDialogDescription>
                This permanently removes the account, and takes back every role and personal grant it
                held with it. This action cannot be undone.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction onClick={handleDelete}>Delete</AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
        )}
        </span>
      </TableCell>
    </TableRow>
  )
}

/**
 * Handing somebody a way back into their account.
 *
 * <h2>⚠️ Why the wording is half the feature</h2>
 *
 * <p>This is the only control on the register that produces a credential **two people know**. That is the
 * whole of what makes it different from disabling or deleting an account, and it is invisible unless the
 * screen says it — so the dialog says it in the sentence above the field rather than in a tooltip. The
 * flag the server sets is what makes the sharing temporary, and that is stated too: somebody who does not
 * know the account is forced to replace it will not think to tell the person to.
 *
 * <h2>⚠️ No generated password, deliberately</h2>
 *
 * <p>A generated one would have to be shown, copied, and carried to the person over whatever channel is
 * to hand — and the screen would be the thing that put a working credential into a chat window. The
 * administrator types what they are going to say out loud, so nothing about the transport is this
 * screen's doing.
 *
 * <h2>⚠️ Absent on a federated row, not disabled</h2>
 *
 * <p>A `GOOGLE` or `GITHUB` account has no password to set — the server refuses it, because writing one
 * would set the change-password flag and lock a working account out of every product. A disabled button
 * would pose the question and answer it with nothing; not offering it, with the reason beside it, answers
 * it.
 */
function SetPasswordButton({ targetUser, isSelf }: { targetUser: AdminUser; isSelf: boolean }) {
  const setPasswordMutation = useSetUserPassword()
  const [open, setOpen] = useState(false)
  const [newPassword, setNewPassword] = useState("")

  if (targetUser.provider !== "LOCAL") {
    return (
      <span className="text-[11.5px] text-muted-foreground">
        signs in through {targetUser.provider.toLowerCase()}
      </span>
    )
  }

  // ⚠️ The server refuses this against one's own account, and for a reason worth repeating: self-service
  // asks for the current password and this path does not, so pointing it at yourself is a way to replace
  // your own credential without proving you know it.
  if (isSelf) {
    return null
  }

  function submit() {
    setPasswordMutation.mutate(
      { id: targetUser.id, newPassword },
      {
        onSuccess: () => {
          toast.success(`${targetUser.email} can sign in with it, once.`)
          setOpen(false)
          setNewPassword("")
        },
        onError: (error) => toast.error(apiErrorMessage(error, "Could not set that password.")),
      },
    )
  }

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger asChild>
        <Button variant="ghost" size="icon" aria-label="Set password">
          <KeyRound className="size-4" />
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Set a password for {targetUser.email}</AlertDialogTitle>
          <AlertDialogDescription>
            You and this person will both know this password. The account is made to replace it at the
            next sign-in, so the sharing lasts until then — tell them what it is, and that they will be
            asked to change it.
          </AlertDialogDescription>
        </AlertDialogHeader>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor={`password-for-${targetUser.id}`}>The password</Label>
          {/* ⚠️ `type="text"`, and it is not an oversight. Masking protects a secret from whoever is
              behind you; this one is being typed in order to be read out. Hiding it here would only make
              a typo undiscoverable, and there is no second field to catch one. */}
          <Input
            id={`password-for-${targetUser.id}`}
            type="text"
            value={newPassword}
            autoComplete="off"
            placeholder="what you are going to tell them"
            onChange={(entry) => setNewPassword(entry.target.value)}
          />
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel onClick={() => setNewPassword("")}>Cancel</AlertDialogCancel>
          {/* ⚠️ A plain Button rather than AlertDialogAction: that one closes the dialog on click, which
              would throw away what was typed the moment the server refuses it. */}
          <Button
            disabled={newPassword.trim().length < 8 || setPasswordMutation.isPending}
            onClick={submit}
          >
            {setPasswordMutation.isPending ? "Setting…" : "Set password"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
