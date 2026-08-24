import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom"
import { isAxiosError } from "axios"
import { Button } from "@/components/ui/button"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { AuthNotice, AuthShell } from "@/components/auth/AuthShell"
import { login } from "@/api/authentication"
import { useAuth } from "@/context/AuthContext"

const loginFormSchema = z.object({
  email: z.string().email("Enter a valid email address"),
  password: z.string().min(1, "Password is required"),
})

type LoginFormValues = z.infer<typeof loginFormSchema>

/**
 * Innoventa's sign-in screen, on Identity's data.
 *
 * ⚠️ <strong>Two things the design mock carries are deliberately absent.</strong> "Sign in without one"
 * and "No account yet? Create one" are real links in Innoventa and would be dead ends here: this
 * service has neither a magic-link flow nor self-service registration (see `CLAUDE.md`, "Open Items").
 * A control that opens nothing is worse than no control, so the footer says the true thing instead.
 */
export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { refresh } = useAuth()
  const [submitError, setSubmitError] = useState<string | null>(null)

  // Google/GitHub sign-in is a server-driven redirect (see the <a href="/oauth2/authorization/..."
  // buttons below) — a rejection there (OAuth2LoginSuccessHandler) can only report back via a query
  // param on the redirect back to this page, not a caught exception like the form path above.
  const [searchParameters] = useSearchParams()
  const oauthError = searchParameters.get("error")
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
          ? "That email and password did not match an account."
          : "Something went wrong. Please try again.",
      )
    }
  }

  return (
    <AuthShell
      title="Sign in"
      subtitle="Identity — one account, every application."
      promise={
        <>
          One account for
          <br />
          everything you build.
        </>
      }
      promises={[
        "Central, Innoventa, Kiwi, Tessera — one sign-in",
        "Google, GitHub, or a password of your own",
        "Every connected client revocable from one page",
      ]}
      footer={
        <Link to="/" className="hover:text-foreground">
          Back to Identity
        </Link>
      }
    >
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-4">
          {oauthErrorMessage && <AuthNotice tone="error">{oauthErrorMessage}</AuthNotice>}

          <FormField
            control={form.control}
            name="email"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Email</FormLabel>
                <FormControl>
                  <Input type="email" autoComplete="username" autoFocus {...field} />
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

          {submitError && <AuthNotice tone="error">{submitError}</AuthNotice>}

          <Button type="submit" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? "Signing in…" : "Sign in"}
          </Button>

          <div className="flex items-center gap-3 text-xs text-muted-foreground">
            <span className="h-px flex-1 bg-border" />
            or continue with
            <span className="h-px flex-1 bg-border" />
          </div>

          {/* Real navigations, not fetches: the provider hand-off is a redirect the backend owns, and
              an XHR to it would follow the redirect and land the HTML in a promise. */}
          <div className="grid grid-cols-2 gap-2">
            <Button type="button" variant="outline" asChild>
              <a href="/oauth2/authorization/google">Google</a>
            </Button>
            <Button type="button" variant="outline" asChild>
              <a href="/oauth2/authorization/github">GitHub</a>
            </Button>
          </div>
        </form>
      </Form>
    </AuthShell>
  )
}
