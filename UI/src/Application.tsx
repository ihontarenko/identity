import { BrowserRouter } from "react-router-dom"
import { QueryClientProvider } from "@tanstack/react-query"
import { Toaster } from "@/components/ui/sonner"
import { queryClient } from "@/lib/queryClient"
import { ThemeProvider } from "@/context/ThemeContext"
import { AuthProvider } from "@/context/AuthContext"
import { ApplicationRoutes } from "@/router"

export function Application() {
  return (
    <ThemeProvider>
      <BrowserRouter>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <ApplicationRoutes />
          </AuthProvider>
          <Toaster />
        </QueryClientProvider>
      </BrowserRouter>
    </ThemeProvider>
  )
}
