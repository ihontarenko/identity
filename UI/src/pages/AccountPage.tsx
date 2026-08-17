import { useEffect } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { toast } from "sonner"
import { isAxiosError } from "axios"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { PageHeader } from "@/components/PageHeader"
import { ConnectedClients } from "@/components/ConnectedClients"
import { useAuth } from "@/context/AuthContext"
import { changePassword, updateProfile } from "@/api/account"

const profileFormSchema = z.object({
  displayName: z.string().min(1, "Display name is required"),
})

const passwordFormSchema = z
  .object({
    currentPassword: z.string().min(1, "Current password is required"),
    newPassword: z.string().min(8, "Password must be at least 8 characters"),
    confirmPassword: z.string().min(1, "Confirm your new password"),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  })

type ProfileFormValues = z.infer<typeof profileFormSchema>
type PasswordFormValues = z.infer<typeof passwordFormSchema>

export function AccountPage() {
  const { user, refresh } = useAuth()

  const profileForm = useForm<ProfileFormValues>({
    resolver: zodResolver(profileFormSchema),
    defaultValues: { displayName: user?.displayName ?? "" },
  })

  useEffect(() => {
    profileForm.reset({ displayName: user?.displayName ?? "" })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.displayName])

  const passwordForm = useForm<PasswordFormValues>({
    resolver: zodResolver(passwordFormSchema),
    defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" },
  })

  async function onSubmitProfile(values: ProfileFormValues) {
    try {
      await updateProfile(values)
      await refresh()
      toast.success("Profile updated.")
    } catch {
      toast.error("Could not update profile.")
    }
  }

  async function onSubmitPassword(values: PasswordFormValues) {
    try {
      await changePassword(values)
      passwordForm.reset()
      toast.success("Password changed.")
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 400) {
        passwordForm.setError("currentPassword", { message: "Current password is incorrect." })
      } else {
        toast.error("Could not change password.")
      }
    }
  }

  if (!user) {
    return null
  }

  return (
    <>
      <PageHeader
        title="Account"
        description={
          <span className="flex items-center gap-2">
            {user.email}
            <Badge variant="secondary">{user.provider}</Badge>
          </span>
        }
      />
      <div className="mx-auto flex w-full max-w-xl flex-col gap-6">
        <Card>
          <CardHeader>
            <CardTitle>Profile</CardTitle>
            <CardDescription>Update your display name.</CardDescription>
          </CardHeader>
          <CardContent>
            <Form {...profileForm}>
              <form onSubmit={profileForm.handleSubmit(onSubmitProfile)} className="flex flex-col gap-4">
                <FormField
                  control={profileForm.control}
                  name="displayName"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Display name</FormLabel>
                      <FormControl>
                        <Input {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <Button type="submit" disabled={profileForm.formState.isSubmitting} className="self-start">
                  Save profile
                </Button>
              </form>
            </Form>
          </CardContent>
        </Card>

        {user.provider === "LOCAL" && (
          <Card>
            <CardHeader>
              <CardTitle>Password</CardTitle>
              <CardDescription>Change your password.</CardDescription>
            </CardHeader>
            <CardContent>
              <Form {...passwordForm}>
                <form onSubmit={passwordForm.handleSubmit(onSubmitPassword)} className="flex flex-col gap-4">
                  <FormField
                    control={passwordForm.control}
                    name="currentPassword"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Current password</FormLabel>
                        <FormControl>
                          <Input type="password" autoComplete="current-password" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={passwordForm.control}
                    name="newPassword"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>New password</FormLabel>
                        <FormControl>
                          <Input type="password" autoComplete="new-password" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={passwordForm.control}
                    name="confirmPassword"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Confirm new password</FormLabel>
                        <FormControl>
                          <Input type="password" autoComplete="new-password" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <Button type="submit" disabled={passwordForm.formState.isSubmitting} className="self-start">
                    Change password
                  </Button>
                </form>
              </Form>
            </CardContent>
          </Card>
        )}

        <ConnectedClients />
      </div>
    </>
  )
}
