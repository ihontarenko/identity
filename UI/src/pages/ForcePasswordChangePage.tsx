import { useState } from "react"
import { isAxiosError } from "axios"
import { toast } from "sonner"
import { KeyRound } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { changePassword } from "@/api/account"
import { useAuth } from "@/context/AuthContext"

function extractErrorMessage(error: unknown, fallback: string) {
  if (isAxiosError(error) && typeof error.response?.data?.detail === "string") {
    return error.response.data.detail as string
  }
  return fallback
}

/**
 * What somebody sees while their account is holding a password an administrator chose for it.
 *
 * ⚠️ This screen is a courtesy, not the gate. The server refuses every other call for such a session
 * — including `/oauth2/authorize`, so the account cannot sign in to any other product either. Rendering
 * this instead of the router is what stops a person wandering into screens that would all fail; it is
 * not what stops them using the password.
 */
export function ForcePasswordChangePage() {
  const { refresh } = useAuth()
  const [currentPassword, setCurrentPassword] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [saving, setSaving] = useState(false)

  const mismatch = confirmPassword.length > 0 && newPassword !== confirmPassword

  async function submit() {
    setSaving(true)
    try {
      await changePassword({ currentPassword, newPassword, confirmPassword })
      toast.success("Password changed.")
      await refresh()
    } catch (error) {
      toast.error(extractErrorMessage(error, "Could not change the password."))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center py-12">
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <KeyRound className="size-5" />
            <CardTitle>Choose your own password</CardTitle>
          </div>
          <CardDescription>
            This account is using a password an administrator set for it, so two people know it. Change
            it now — until you do, it opens nothing here and nothing in any application that signs in
            through Identity.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="currentPassword">The password you were given</Label>
            <Input
              id="currentPassword"
              type="password"
              value={currentPassword}
              onChange={(entry) => setCurrentPassword(entry.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="newPassword">New password</Label>
            <Input
              id="newPassword"
              type="password"
              value={newPassword}
              onChange={(entry) => setNewPassword(entry.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="confirmPassword">Repeat it</Label>
            <Input
              id="confirmPassword"
              type="password"
              value={confirmPassword}
              onChange={(entry) => setConfirmPassword(entry.target.value)}
              aria-invalid={mismatch}
            />
            {mismatch && <p className="text-[11.5px] text-destructive">The two do not match.</p>}
          </div>
          <Button
            className="mt-1"
            disabled={
              saving || !currentPassword || newPassword.length < 8 || newPassword !== confirmPassword
            }
            onClick={submit}
          >
            Change password
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
