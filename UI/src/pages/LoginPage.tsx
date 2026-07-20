import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom"
import { isAxiosError } from "axios"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { Separator } from "@/components/ui/separator"
import { login } from "@/api/authentication"
import { useAuth } from "@/context/AuthContext"

const loginFormSchema = z.object({
  email: z.string().email("Enter a valid email address"),
  password: z.string().min(1, "Password is required"),
})

type LoginFormValues = z.infer<typeof loginFormSchema>

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { refresh } = useAuth()
  const [submitError, setSubmitError] = useState<string | null>(null)

  // Google/GitHub sign-in is a server-driven redirect (see the <a href="/oauth2/authorization/..."
  // buttons below) — a rejection there (OAuth2LoginSuccessHandler) can only report back via a query
  // param on the redirect back to this page, not a caught exception like the form path above.
  const [searchParams] = useSearchParams()
  const oauthError = searchParams.get("error")
  const oauthErrorMessage =
    oauthError === "disabled"
      ? "That account has been disabled. Contact an administrator."
      : oauthError
        ? "Could not sign you in. Please try again."
        : null

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginFormSchema),
    defaultValues: { email: "", password: "" },
  })

  async function onSubmit(values: LoginFormValues) {
    setSubmitError(null)
    try {
      const result = await login(values)
      await refresh()
      if (result.redirectTo) {
        window.location.href = result.redirectTo
        return
      }
      const fallback = (location.state as { from?: string } | null)?.from ?? "/account"
      navigate(fallback, { replace: true })
    } catch (error) {
      setSubmitError(
        isAxiosError(error) && error.response?.status === 401
          ? "Invalid email or password."
          : "Something went wrong. Please try again.",
      )
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center">
      <Card>
        <CardHeader>
          <CardTitle>Sign in</CardTitle>
          <CardDescription>Sign in to manage your Identity account.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {oauthErrorMessage && <p className="text-sm text-destructive">{oauthErrorMessage}</p>}
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-4">
              <FormField
                control={form.control}
                name="email"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Email</FormLabel>
                    <FormControl>
                      <Input type="email" autoComplete="email" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="password"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Password</FormLabel>
                    <FormControl>
                      <Input type="password" autoComplete="current-password" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              {submitError && <p className="text-sm text-destructive">{submitError}</p>}
              <Button type="submit" disabled={form.formState.isSubmitting}>
                Sign in
              </Button>
            </form>
          </Form>

          <div className="flex items-center gap-3">
            <Separator className="flex-1" />
            <span className="text-xs text-muted-foreground">or continue with</span>
            <Separator className="flex-1" />
          </div>

          <div className="flex flex-col gap-2">
            <Button variant="outline" asChild>
              <a href="/oauth2/authorization/google">Continue with Google</a>
            </Button>
            <Button variant="outline" asChild>
              <a href="/oauth2/authorization/github">Continue with GitHub</a>
            </Button>
          </div>
        </CardContent>
      </Card>
      <p className="mt-4 text-center text-sm text-muted-foreground">
        <Link to="/" className="hover:text-foreground">
          Back to Identity
        </Link>
      </p>
    </div>
  )
}
