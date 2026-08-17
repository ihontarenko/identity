import { useState } from "react"
import { isAxiosError } from "axios"
import { toast } from "sonner"
import { RefreshCw, ShieldAlert, Trash2 } from "lucide-react"
import { PageHeader } from "@/components/PageHeader"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import {
  nameOf,
  useAccessOverview,
  useAssignRole,
  useGrantPermission,
  useSetRoleBundle,
  useUnassignRole,
  useUngrantPermission,
  type AccessOverview,
  type RoleView,
} from "@/api/access"

function extractErrorMessage(error: unknown, fallback: string) {
  if (isAxiosError(error) && typeof error.response?.data?.detail === "string") {
    return error.response.data.detail as string
  }
  return fallback
}

export function AccessSettingsPage() {
  const { data: overview, isLoading, isError, error, refetch } = useAccessOverview()

  return (
    <>
      <PageHeader
        title="Access"
        description="What the roles carry, who holds them, and what one person holds personally."
      />

      {isError && (
        <div className="flex items-center justify-between gap-3 rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2 text-[13px]">
          <span className="text-destructive">
            {extractErrorMessage(error, "Could not load access.")}
          </span>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            <RefreshCw className="size-3.5" />
            Retry
          </Button>
        </div>
      )}

      {isLoading && <p className="text-[12.5px] text-muted-foreground">Loading…</p>}

      {overview && (
        <Tabs defaultValue="roles">
          <TabsList>
            <TabsTrigger value="roles">Roles</TabsTrigger>
            <TabsTrigger value="holders">Holders</TabsTrigger>
            <TabsTrigger value="overrides">Overrides</TabsTrigger>
          </TabsList>

          <TabsContent value="roles">
            <RolesTab overview={overview} />
          </TabsContent>
          <TabsContent value="holders">
            <HoldersTab overview={overview} />
          </TabsContent>
          <TabsContent value="overrides">
            <OverridesTab overview={overview} />
          </TabsContent>
        </Tabs>
      )}
    </>
  )
}

// ── Roles ────────────────────────────────────────────────────────────────────

function RolesTab({ overview }: { overview: AccessOverview }) {
  return (
    <div className="flex flex-col gap-6">
      <p className="text-[11.5px] text-muted-foreground">
        Editing a role changes it for everybody holding it, at once — which is why this screen is
        behind a permission of its own. Roles are neither created nor deleted here: a role invented at
        runtime is a name no code hands out.
      </p>
      {overview.roles.map((role) => (
        <RoleCard key={role.name} role={role} overview={overview} />
      ))}
    </div>
  )
}

function RoleCard({ role, overview }: { role: RoleView; overview: AccessOverview }) {
  const [carried, setCarried] = useState<string[]>(role.bundle.map((entry) => entry.permission))
  const setBundleMutation = useSetRoleBundle()

  const changed =
    carried.length !== role.bundle.length ||
    carried.some((permission) => !role.bundle.some((entry) => entry.permission === permission))

  function toggle(permission: string, on: boolean) {
    setCarried((current) =>
      on ? [...current, permission] : current.filter((held) => held !== permission),
    )
  }

  function save() {
    setBundleMutation.mutate(
      {
        roleName: role.name,
        // Every line is carried at the scope the role is assignable at. Identity has one floor a role
        // can sit at, so there is nothing to choose and no control offering the choice.
        bundle: carried.map((permission) => ({ permission, carriedAt: role.assignableAt })),
      },
      {
        onSuccess: () => toast.success(`${role.name} updated.`),
        onError: (error) => toast.error(extractErrorMessage(error, "Could not save the role.")),
      },
    )
  }

  return (
    <div className="rounded-md border p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="font-display text-[15px]">{role.name}</h3>
          <p className="mt-0.5 text-[11.5px] text-muted-foreground">
            Assignable at <Badge variant="outline">{role.assignableAt}</Badge>
          </p>
        </div>
        <Button size="sm" disabled={!changed || setBundleMutation.isPending} onClick={save}>
          Save
        </Button>
      </div>

      {role.declared && (
        <p className="mt-3 flex items-start gap-1.5 text-[11.5px] text-muted-foreground">
          <ShieldAlert className="mt-px size-3.5 shrink-0" />
          <span>
            <code className="font-mono">policy/identity.jmp</code> declares this role, so an edit here
            lasts until somebody changes that file — the next start after it moves rewrites this
            bundle from the document.
          </span>
        </p>
      )}

      <div className="mt-4 flex flex-col gap-2">
        {overview.permissions.map((permission) => (
          <label
            key={permission.name}
            className="flex items-center justify-between gap-4 rounded-sm px-1 py-1 hover:bg-muted/50"
          >
            <span>
              <span className="font-mono text-[12.5px]">{permission.name}</span>
              <span className="ml-2 text-[11.5px] text-muted-foreground">
                {permission.description}
              </span>
            </span>
            <Switch
              checked={carried.includes(permission.name)}
              onCheckedChange={(on) => toggle(permission.name, on)}
            />
          </label>
        ))}
      </div>
    </div>
  )
}

// ── Holders ──────────────────────────────────────────────────────────────────

function HoldersTab({ overview }: { overview: AccessOverview }) {
  const [accountId, setAccountId] = useState("")
  const [roleName, setRoleName] = useState("")
  const assignMutation = useAssignRole()
  const unassignMutation = useUnassignRole()

  function assign() {
    assignMutation.mutate(
      { accountId, roleName },
      {
        onSuccess: () => {
          toast.success("Role granted.")
          setAccountId("")
          setRoleName("")
        },
        onError: (error) => toast.error(extractErrorMessage(error, "Could not grant the role.")),
      },
    )
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-end gap-3 rounded-md border p-3">
        <div className="flex min-w-56 flex-1 flex-col gap-1.5">
          <Label>Person</Label>
          <Select value={accountId} onValueChange={setAccountId}>
            <SelectTrigger>
              <SelectValue placeholder="Choose an account" />
            </SelectTrigger>
            <SelectContent>
              {overview.accounts.map((account) => (
                <SelectItem key={account.id} value={account.id}>
                  {nameOf(account)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex min-w-56 flex-1 flex-col gap-1.5">
          <Label>Role</Label>
          <Select value={roleName} onValueChange={setRoleName}>
            <SelectTrigger>
              <SelectValue placeholder="Choose a role" />
            </SelectTrigger>
            <SelectContent>
              {overview.roles.map((role) => (
                <SelectItem key={role.name} value={role.name}>
                  {role.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Button disabled={!accountId || !roleName || assignMutation.isPending} onClick={assign}>
          Grant
        </Button>
      </div>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Person</TableHead>
            <TableHead>Role</TableHead>
            {/* ⚠️ Always GLOBAL here, and rendered anyway. A screen that hides the scope teaches the
                reader that grants have no scope — which the Overrides tab then contradicts. */}
            <TableHead>Scope</TableHead>
            <TableHead>Source</TableHead>
            <TableHead className="text-right">Take back</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {overview.roleHoldings.length === 0 && (
            <TableRow>
              <TableCell colSpan={5} className="text-center text-muted-foreground">
                Nobody holds a role.
              </TableCell>
            </TableRow>
          )}
          {overview.roleHoldings.map((holding) => (
            <TableRow key={`${holding.account?.id ?? "gone"}-${holding.roleName}`}>
              <TableCell className={holding.account ? "" : "text-muted-foreground italic"}>
                {nameOf(holding.account)}
              </TableCell>
              <TableCell className="font-mono text-[12.5px]">{holding.roleName}</TableCell>
              <TableCell>
                <Badge variant="outline">{holding.scopeType}</Badge>
              </TableCell>
              <TableCell className="text-[11.5px] text-muted-foreground">{holding.source}</TableCell>
              <TableCell className="text-right">
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label="Take back the role"
                  disabled={!holding.account || unassignMutation.isPending}
                  onClick={() =>
                    unassignMutation.mutate(
                      { accountId: holding.account!.id, roleName: holding.roleName },
                      {
                        onSuccess: () => toast.success("Role taken back."),
                        onError: (error) =>
                          toast.error(extractErrorMessage(error, "Could not take the role back.")),
                      },
                    )
                  }
                >
                  <Trash2 className="size-4 text-destructive" />
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

// ── Overrides ────────────────────────────────────────────────────────────────

function OverridesTab({ overview }: { overview: AccessOverview }) {
  const [accountId, setAccountId] = useState("")
  const [permission, setPermission] = useState("")
  const [allowed, setAllowed] = useState(true)
  const [reason, setReason] = useState("")
  const grantMutation = useGrantPermission()
  const ungrantMutation = useUngrantPermission()

  function submit() {
    grantMutation.mutate(
      { accountId, permission, allowed, reason },
      {
        onSuccess: () => {
          toast.success(allowed ? "Permission granted." : "Permission denied.")
          setAccountId("")
          setPermission("")
          setReason("")
        },
        onError: (error) => toast.error(extractErrorMessage(error, "Could not save the override.")),
      },
    )
  }

  return (
    <div className="flex flex-col gap-5">
      <p className="text-[11.5px] text-muted-foreground">
        A <strong>deny wins over every role that grants it</strong>, and the subtraction runs last — so
        this is the sharpest control here, and the reason a reason is required. It is also the only way{" "}
        <code className="font-mono">user:delete</code> is ever held: no role carries it, deliberately,
        so that “who can permanently delete an account” is a list of named people.
      </p>

      <div className="flex flex-wrap items-end gap-3 rounded-md border p-3">
        <div className="flex min-w-52 flex-1 flex-col gap-1.5">
          <Label>Person</Label>
          <Select value={accountId} onValueChange={setAccountId}>
            <SelectTrigger>
              <SelectValue placeholder="Choose an account" />
            </SelectTrigger>
            <SelectContent>
              {overview.accounts.map((account) => (
                <SelectItem key={account.id} value={account.id}>
                  {nameOf(account)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex min-w-52 flex-1 flex-col gap-1.5">
          <Label>Permission</Label>
          <Select value={permission} onValueChange={setPermission}>
            <SelectTrigger>
              <SelectValue placeholder="Choose a permission" />
            </SelectTrigger>
            <SelectContent>
              {overview.permissions.map((known) => (
                <SelectItem key={known.name} value={known.name}>
                  {known.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex flex-col gap-1.5">
          <Label>Effect</Label>
          <div className="flex h-[34px] items-center gap-2">
            <Switch checked={allowed} onCheckedChange={setAllowed} />
            <span className="text-[12.5px]">{allowed ? "Allow" : "Deny"}</span>
          </div>
        </div>
        <div className="flex min-w-64 flex-1 flex-col gap-1.5">
          <Label>Reason</Label>
          <Input
            value={reason}
            onChange={(entry) => setReason(entry.target.value)}
            placeholder="Why — it outlives whoever wrote it"
          />
        </div>
        <Button
          disabled={!accountId || !permission || !reason.trim() || grantMutation.isPending}
          onClick={submit}
        >
          Save
        </Button>
      </div>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Person</TableHead>
            <TableHead>Permission</TableHead>
            <TableHead>Effect</TableHead>
            <TableHead>Scope</TableHead>
            <TableHead>Reason</TableHead>
            <TableHead className="text-right">Take back</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {overview.directHoldings.length === 0 && (
            <TableRow>
              <TableCell colSpan={6} className="text-center text-muted-foreground">
                Nobody holds a personal allow or deny.
              </TableCell>
            </TableRow>
          )}
          {overview.directHoldings.map((holding) => (
            <TableRow key={`${holding.account?.id ?? "gone"}-${holding.permission}`}>
              <TableCell className={holding.account ? "" : "text-muted-foreground italic"}>
                {nameOf(holding.account)}
              </TableCell>
              <TableCell className="font-mono text-[12.5px]">{holding.permission}</TableCell>
              <TableCell>
                <Badge variant={holding.allowed ? "outline" : "destructive"}>
                  {holding.allowed ? "Allow" : "Deny"}
                </Badge>
              </TableCell>
              <TableCell>
                <Badge variant="outline">{holding.scopeType}</Badge>
              </TableCell>
              <TableCell className="text-[11.5px] text-muted-foreground">{holding.reason}</TableCell>
              <TableCell className="text-right">
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label="Take back the override"
                  disabled={!holding.account || ungrantMutation.isPending}
                  onClick={() =>
                    ungrantMutation.mutate(
                      { accountId: holding.account!.id, permission: holding.permission },
                      {
                        onSuccess: () => toast.success("Override taken back."),
                        onError: (error) =>
                          toast.error(extractErrorMessage(error, "Could not take it back.")),
                      },
                    )
                  }
                >
                  <Trash2 className="size-4 text-destructive" />
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
