import { useMemo, useState } from "react"
import { ShieldCheck } from "lucide-react"
import { toast } from "sonner"
import { PageHeader } from "@/components/PageHeader"
import { EmptyState } from "@/components/EmptyState"
import { AccountChip } from "@/components/AccountChip"
import { HighlightedCode } from "@/components/HighlightedCode"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  useAccessOverview,
  useAssignRole,
  useGrantPermission,
  usePolicyProjection,
  useSetRoleBundle,
  useUnassignRole,
  useUngrantPermission,
  type AccountReference,
  type AccessOverview,
  type BundleEntryView,
  type DirectHoldingView,
  type RoleHoldingView,
  type RoleView,
} from "@/api/access"
import { apiErrorMessage } from "@/api/errors"

/**
 * The installation's access screen.
 *
 * <h2>⚠️ This is WiQ's access screen, and the likeness is the specification</h2>
 *
 * <p>`WiQ/UI/src/pages/AccessSettingsPage.tsx` is the reference — same four tabs in the same order, same
 * switch matrix per role, same tables, same `rounded-lg border p-4` section rhythm. (WiQ took it from
 * Tessera, whose copy still has three: the projection tab is WIQ-15, and TSSR-20 asking for the same one
 * there is not built.) Where this one differs it is because the *domain* differs and nowhere else: Tessera
 * scopes a holding to a project and WiQ to a section, whereas **Identity has one floor and only one** —
 * every holding here is `GLOBAL`, so there is no place to pick and no column to fill in. A layout invented
 * here instead would be a third screen to maintain and a third thing to learn.
 *
 * <p>⚠️ **What it edits is in force on the next request.** The engine resolves every route from these
 * rows; `policy/identity.jmp` is only what a fresh installation was born with. That is the whole point of
 * the screen — authorization changes without a deploy — and the reason it sits behind `access:administer`.
 *
 * <p>⚠️ **And what it edits on a *declared* role does not last.** The seed rewrites the bundle of every
 * role the document declares whenever that document changes, so an edit to one survives until somebody
 * edits the file. The banner says so, because the alternative is an administrator discovering it after a
 * deploy and concluding the screen is broken.
 *
 * <p>The first three tabs are three different questions, and keeping them apart is deliberate: what a role
 * *means*, who *holds* one, and what somebody was given or refused *personally*. The third is where a
 * surprise usually lives — a deny beats every role that grants it, so a person who "should" be able to do
 * something and cannot is almost always in that table. The fourth answers none of the three and is the only
 * one that answers **all of them at once**: the whole of the authorization in force, rendered back into the
 * language it is declared in.
 */
export function AccessSettingsPage() {
  const { data, isLoading, isError, refetch } = useAccessOverview()

  return (
    <>
      <PageHeader title="Access" description="What each role carries, and who holds what" />

      {isLoading && (
        <div className="space-y-2">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      )}

      {isError && (
        <EmptyState
          icon={ShieldCheck}
          title="Could not load access"
          message="Nothing was changed. Either the request failed or this account no longer holds access:administer."
          action={
            <Button size="sm" variant="outline" onClick={() => refetch()}>
              Try again
            </Button>
          }
        />
      )}

      {!isLoading && data && <AccessTabs overview={data} />}
    </>
  )
}

function AccessTabs({ overview }: { overview: AccessOverview }) {
  return (
    <Tabs defaultValue="roles" className="space-y-4">
      <TabsList>
        <TabsTrigger value="roles">Roles</TabsTrigger>
        <TabsTrigger value="holdings">Who holds what</TabsTrigger>
        <TabsTrigger value="personal">Personal grants</TabsTrigger>
        <TabsTrigger value="projection">As a policy</TabsTrigger>
      </TabsList>

      <TabsContent value="roles" className="space-y-6">
        {overview.roles.map((role) => (
          <RoleCard key={role.name} role={role} permissions={overview.permissions} />
        ))}
      </TabsContent>

      <TabsContent value="holdings">
        <RoleHoldings overview={overview} />
      </TabsContent>

      <TabsContent value="personal">
        <DirectHoldings overview={overview} />
      </TabsContent>

      <TabsContent value="projection">
        <PolicyProjectionTab />
      </TabsContent>
    </Tabs>
  )
}

/**
 * What is actually in force, as a `.jmp` document.
 *
 * ⚠️ **The file on disk is the seed; this is the truth.** `policy/identity.jmp` is what a fresh
 * installation was born with, and it drifts from the tables the moment somebody edits a bundle on this
 * very screen. There is otherwise nowhere to read the whole of it in one piece — the other three tabs are
 * three lists, and a person answering "who can do what here" assembles it in their head.
 *
 * ⚠️ **Read-only, and it stays that way.** No editing, no import, no "apply this file" — the screen writes
 * through the administration port, which validates every change against the catalogues. A textarea that
 * re-seeded from pasted text would be a second write path with none of that.
 */
function PolicyProjectionTab() {
  const projection = usePolicyProjection()

  if (projection.isLoading) {
    return <Skeleton className="h-96 w-full" />
  }

  // ⚠️ A failed fetch must SAY SO, and this is the one tab where that is easy to get wrong: `data` is a
  // string, so the obvious `data ?? ""` renders an empty bordered box — a screen that looks like an
  // installation with no authorization at all rather than like a request that did not arrive. The first
  // reading of this tab was exactly that: a new interface talking to a backend too old to have the route.
  if (projection.isError || projection.data === undefined) {
    return (
      <EmptyState
        icon={ShieldCheck}
        title="Could not render the policy"
        message="Nothing is wrong with the authorization itself — the other three tabs read it from a different route. This one either failed or is not there, which is what a backend older than the tab looks like."
        action={
          <Button size="sm" variant="outline" onClick={() => projection.refetch()}>
            Try again
          </Button>
        }
      />
    )
  }

  // ⚠️ **A 200 is not proof this is a policy, and in Identity specifically it often is not.** The session
  // filter chain turns an unauthorized request into a redirect to `/login`, and axios follows it — so a
  // stale session answers this tab with the login page's HTML at status 200. Rendering that through the
  // `.jmp` grammar would colour a login form as authorization. A rendered policy always opens with its
  // generated header, so the cheapest honest test is whether it did.
  if (!projection.data.trimStart().startsWith("#")) {
    return (
      <EmptyState
        icon={ShieldCheck}
        title="That was not a policy"
        message="The route answered, but with something other than a rendered document — an empty body, or the sign-in page arrived at through a redirect. Signing in again is the usual fix."
        action={
          <Button size="sm" variant="outline" onClick={() => projection.refetch()}>
            Try again
          </Button>
        }
      />
    )
  }

  // ⚠️ Highlighted through the `.jmp` grammar, which exists for exactly this document. A policy read as
  // undifferentiated grey is one whose `deny` lines get skimmed past, and a deny is the sharpest line in
  // the file.
  return <HighlightedCode code={projection.data} language="jmp" />
}

/**
 * One role and everything it carries, as a switch per permission.
 *
 * A matrix rather than a picker: what matters when reading a role is what it does *not* carry, and a list
 * of only the granted lines cannot show that. `assignableAt` decides the scope every entry is written at
 * — and Identity has exactly one floor a role can sit at — so there is no second control for it, and
 * nothing to get wrong.
 *
 * ⚠️ Roles are neither created nor deleted here: a role invented at runtime is a name no code hands out.
 */
function RoleCard({ role, permissions }: { role: RoleView; permissions: AccessOverview["permissions"] }) {
  const [carried, setCarried] = useState<Set<string>>(
    () => new Set(role.bundle.map((entry) => entry.permission)),
  )

  const original = useMemo(() => new Set(role.bundle.map((entry) => entry.permission)), [role.bundle])

  const changed = carried.size !== original.size || [...carried].some((name) => !original.has(name))

  const save = useSetRoleBundle()

  function toggle(permission: string) {
    setCarried((previous) => {
      const next = new Set(previous)

      if (next.has(permission)) {
        next.delete(permission)
      } else {
        next.add(permission)
      }

      return next
    })
  }

  function persist() {
    const bundle: BundleEntryView[] = [...carried].map((permission) => ({
      permission,
      carriedAt: role.assignableAt,
    }))

    save.mutate(
      { roleName: role.name, bundle },
      {
        onSuccess: () => toast.success(`${role.name} updated — in force on the next request`),
        onError: (error) => toast.error(apiErrorMessage(error, "Could not save that")),
      },
    )
  }

  return (
    <section className="rounded-lg border p-4">
      <header className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h3 className="font-medium">{role.name}</h3>
          <Badge variant="outline">assignable at {role.assignableAt}</Badge>
        </div>
        <Button size="sm" disabled={!changed || save.isPending} onClick={persist}>
          {save.isPending ? "Saving…" : "Save"}
        </Button>
      </header>

      {role.declared && (
        <Alert className="mb-3">
          <AlertTitle>The policy document declares this role</AlertTitle>
          <AlertDescription>
            An edit here is in force immediately and is rewritten from <code>policy/identity.jmp</code>{" "}
            the next time that file changes. For a permanent change, edit the document.
          </AlertDescription>
        </Alert>
      )}

      <ul className="grid gap-2 sm:grid-cols-2">
        {permissions.map((permission) => (
          <li key={permission.name} className="flex items-start gap-3 rounded-md border p-2">
            <Switch
              checked={carried.has(permission.name)}
              onCheckedChange={() => toggle(permission.name)}
              aria-label={permission.name}
            />
            <div className="min-w-0">
              <div className="truncate font-mono text-xs">{permission.name}</div>
              <div className="text-xs text-muted-foreground">{permission.description}</div>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}

function RoleHoldings({ overview }: { overview: AccessOverview }) {
  return (
    <div className="space-y-4">
      <AssignRoleForm overview={overview} />
      {overview.roleHoldings.length === 0 ? (
        <EmptyState
          icon={ShieldCheck}
          title="Nobody holds a role yet"
          message="Until somebody is given one, this installation has exactly the powers the policy document was born with."
        />
      ) : (
        <RoleHoldingsTable overview={overview} />
      )}
    </div>
  )
}

function RoleHoldingsTable({ overview }: { overview: AccessOverview }) {
  const remove = useUnassignRole()

  function takeBack(holding: RoleHoldingView) {
    remove.mutate(
      { accountId: holding.account!.id, roleName: holding.roleName },
      {
        onSuccess: () => toast.success("Taken back"),
        onError: (error) => toast.error(apiErrorMessage(error, "Could not take that back")),
      },
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Account</TableHead>
          <TableHead>Role</TableHead>
          <TableHead>Where</TableHead>
          <TableHead className="w-28">Source</TableHead>
          <TableHead className="w-24" />
        </TableRow>
      </TableHeader>
      <TableBody>
        {overview.roleHoldings.map((holding, index) => (
          <TableRow key={`${holding.account?.id ?? "gone"}-${holding.roleName}-${index}`}>
            <TableCell>
              <HolderCell account={holding.account} />
            </TableCell>
            <TableCell className="font-mono text-xs">{holding.roleName}</TableCell>
            <TableCell>{describePlace(holding)}</TableCell>
            <TableCell className="text-xs text-muted-foreground">{holding.source}</TableCell>
            <TableCell>
              <TakeBackButton
                account={holding.account}
                label="Take back"
                pending={remove.isPending}
                onTakeBack={() => takeBack(holding)}
              />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

/**
 * Give somebody a role.
 *
 * ⚠️ **There is no third field, and its absence is the point.** Tessera asks *where* because a role there
 * may be assignable at a project; every role here is assignable at the one floor Identity has, so a
 * picker offering one answer would be a control that teaches a distinction the domain does not make.
 */
function AssignRoleForm({ overview }: { overview: AccessOverview }) {
  const [accountId, setAccountId] = useState("")
  const [roleName, setRoleName] = useState("")

  const assign = useAssignRole()

  const role = overview.roles.find((candidate) => candidate.name === roleName)
  const ready = accountId !== "" && roleName !== ""

  function give() {
    assign.mutate(
      { accountId, roleName },
      {
        onSuccess: () => {
          toast.success("Given — in force on the next request")
          setAccountId("")
          setRoleName("")
        },
        onError: (error) => toast.error(apiErrorMessage(error, "Could not grant that")),
      },
    )
  }

  return (
    <section className="rounded-lg border p-4">
      <h3 className="mb-3 text-sm font-medium">Give a role</h3>
      <div className="flex flex-wrap items-end gap-2">
        <PickerField label="Account" value={accountId} onChange={setAccountId}>
          {overview.accounts.map((account) => (
            <option key={account.id} value={account.id}>
              {account.displayName ?? account.email}
            </option>
          ))}
        </PickerField>

        <PickerField label="Role" value={roleName} onChange={setRoleName}>
          {overview.roles.map((candidate) => (
            <option key={candidate.name} value={candidate.name}>
              {candidate.name}
            </option>
          ))}
        </PickerField>

        <Button size="sm" disabled={!ready || assign.isPending} onClick={give}>
          {assign.isPending ? "Giving…" : "Give"}
        </Button>
      </div>

      {role && (
        <p className="mt-2 text-xs text-muted-foreground">
          ⚠️ {role.name} is installation-wide — it applies everywhere, including to whatever this service
          is asked to answer for next.
        </p>
      )}
    </section>
  )
}

/**
 * Personal allow and deny.
 *
 * ⚠️ **Deny wins over every role that grants it, and the subtraction runs last.** This is the table to
 * read when somebody insists they should be able to do something and cannot.
 */
function DirectHoldings({ overview }: { overview: AccessOverview }) {
  return (
    <div className="space-y-4">
      <GrantPermissionForm overview={overview} />
      {overview.directHoldings.length === 0 ? (
        <EmptyState
          icon={ShieldCheck}
          title="Nobody has a personal grant"
          message="Everything anybody may do comes from a role, which is the healthy state. A personal grant is for the exception a role should not be reshaped around."
        />
      ) : (
        <DirectHoldingsTable overview={overview} />
      )}
    </div>
  )
}

/**
 * Hand one permission to one person, or take one away.
 *
 * ⚠️ **A deny wins over every role that grants it.** It is the sharpest instrument on this screen and the
 * reason the reason field is required: somebody reading this table in a year has only that sentence to go
 * on.
 */
function GrantPermissionForm({ overview }: { overview: AccessOverview }) {
  const [accountId, setAccountId] = useState("")
  const [permission, setPermission] = useState("")
  const [allowed, setAllowed] = useState(false)
  const [reason, setReason] = useState("")

  const save = useGrantPermission()

  const ready = accountId !== "" && permission !== "" && reason.trim() !== ""

  function persist() {
    save.mutate(
      { accountId, permission, allowed, reason },
      {
        onSuccess: () => {
          toast.success(allowed ? "Allowed" : "Denied — this beats every role that grants it.")
          setAccountId("")
          setPermission("")
          setReason("")
        },
        onError: (error) => toast.error(apiErrorMessage(error, "Could not save that")),
      },
    )
  }

  return (
    <section className="rounded-lg border p-4">
      <h3 className="mb-3 text-sm font-medium">Allow or deny one person</h3>

      <div className="flex flex-wrap items-end gap-2">
        <PickerField label="Account" value={accountId} onChange={setAccountId}>
          {overview.accounts.map((account) => (
            <option key={account.id} value={account.id}>
              {account.displayName ?? account.email}
            </option>
          ))}
        </PickerField>

        <PickerField label="Permission" value={permission} onChange={setPermission}>
          {overview.permissions.map((candidate) => (
            <option key={candidate.name} value={candidate.name}>
              {candidate.name}
            </option>
          ))}
        </PickerField>

        <div className="flex items-center gap-2 pb-1">
          <Switch checked={allowed} onCheckedChange={setAllowed} aria-label="allow" />
          <span className="text-xs">{allowed ? "allow" : "deny"}</span>
        </div>
      </div>

      <div className="mt-2 flex items-end gap-2">
        <div className="flex-1">
          <label className="mb-1 block text-xs text-muted-foreground" htmlFor="grant-reason">
            Why — required, and read by whoever asks about this in a year
          </label>
          <Input
            id="grant-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="Contractor: read-only until the review"
          />
        </div>
        <Button size="sm" disabled={!ready || save.isPending} onClick={persist}>
          {save.isPending ? "Saving…" : allowed ? "Allow" : "Deny"}
        </Button>
      </div>

      {/* ⚠️ Not a footnote: no role carries `user:delete`, deliberately, so this form is the *only* way
          anybody ever holds it — which makes "who can permanently delete an account" a list of named
          people rather than a property of a role somebody might widen by accident. */}
      <p className="mt-2 text-xs text-muted-foreground">
        ⚠️ This is also the only way <code className="font-mono">user:delete</code> is ever held — no role
        carries it, so the people who may permanently delete an account are exactly the rows below.
      </p>

      {!allowed && permission !== "" && (
        <p className="mt-2 text-xs text-muted-foreground">
          ⚠️ A deny beats every role that grants {permission}. It is the only way to take it from one
          person without editing the role that gives it to everybody else — and the only way to give it
          back is to remove this row.
        </p>
      )}
    </section>
  )
}

function DirectHoldingsTable({ overview }: { overview: AccessOverview }) {
  const remove = useUngrantPermission()

  function takeBack(holding: DirectHoldingView) {
    remove.mutate(
      { accountId: holding.account!.id, permission: holding.permission },
      {
        onSuccess: () => toast.success("Removed — whatever the roles say applies again"),
        onError: (error) => toast.error(apiErrorMessage(error, "Could not take that back")),
      },
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Account</TableHead>
          <TableHead>Permission</TableHead>
          <TableHead className="w-24">Effect</TableHead>
          <TableHead>Where</TableHead>
          <TableHead>Reason</TableHead>
          <TableHead className="w-24" />
        </TableRow>
      </TableHeader>
      <TableBody>
        {overview.directHoldings.map((holding, index) => (
          <TableRow key={`${holding.account?.id ?? "gone"}-${holding.permission}-${index}`}>
            <TableCell>
              <HolderCell account={holding.account} />
            </TableCell>
            <TableCell className="font-mono text-xs">{holding.permission}</TableCell>
            <TableCell>
              <Badge variant={holding.allowed ? "outline" : "destructive"}>
                {holding.allowed ? "allow" : "deny"}
              </Badge>
            </TableCell>
            <TableCell>{describePlace(holding)}</TableCell>
            <TableCell className="text-xs text-muted-foreground">{holding.reason}</TableCell>
            <TableCell>
              <TakeBackButton
                account={holding.account}
                label="Remove"
                pending={remove.isPending}
                onTakeBack={() => takeBack(holding)}
              />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

/**
 * Who holds it — or the sentence that says nobody does any more.
 *
 * ⚠️ **Both holding tables render this, and that is why it is one component.** An orphaned holding is
 * a row whose account has been deleted out from under it, and the two tables were saying so in two
 * copies of the same conditional. A wording that drifts between them reads as two different situations
 * to whoever is looking at the screen, when it is one.
 */
function HolderCell({ account }: { account: AccountReference | null }) {
  if (!account) {
    return <span className="text-xs text-muted-foreground">an account that no longer exists</span>
  }

  return <AccountChip account={account} subtitle={account.email} />
}

/**
 * Withdrawing one holding.
 *
 * ⚠️ **It renders nothing when there is no account, and that is the rule rather than a style.** An
 * orphaned holding has nobody to address a withdrawal to — clearing those is a database job, and saying
 * so beats offering a button that cannot work. Both tables need exactly that behaviour, so it lives
 * here once instead of in two conditionals that could stop agreeing.
 */
function TakeBackButton(
  { account, label, pending, onTakeBack }: {
    account: AccountReference | null
    label: string
    pending: boolean
    onTakeBack: () => void
  },
) {
  if (!account) {
    return null
  }

  return (
    <Button size="sm" variant="ghost" disabled={pending} onClick={onTakeBack}>
      {label}
    </Button>
  )
}

/** A labelled select with an explicit empty choice, so "not chosen" is never mistaken for the first option. */
function PickerField({
  label,
  value,
  onChange,
  emptyLabel,
  children,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  emptyLabel?: string
  children: React.ReactNode
}) {
  return (
    <div>
      <label className="mb-1 block text-xs text-muted-foreground">{label}</label>
      <select
        className="h-9 rounded-md border bg-background px-2 text-sm"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">{emptyLabel ?? "Choose…"}</option>
        {children}
      </select>
    </div>
  )
}

/**
 * Where a holding applies.
 *
 * ⚠️ **Always the same answer here, and rendered anyway.** Identity has one floor, so this column says
 * `global` on every row — but a screen that hides the scope teaches its reader that grants have none,
 * and the same screen in Tessera and WiQ shows a real project or a real branch in this exact column.
 */
function describePlace(holding: { scopeType: string }) {
  return <span className="text-xs text-muted-foreground">{holding.scopeType.toLowerCase()}</span>
}
