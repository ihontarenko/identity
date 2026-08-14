import { useQuery } from "@tanstack/react-query"
import { httpClient } from "@/api/httpClient"

export interface ApplicationLinks {
  innoventaUrl: string | null
  monetaUrl: string | null
  centralUrl: string | null
  tesseraUrl: string | null
}

async function fetchApplicationLinks(): Promise<ApplicationLinks> {
  const response = await httpClient.get<ApplicationLinks>("/application-links")
  return response.data
}

export function useApplicationLinks() {
  return useQuery({
    queryKey: ["application-links"],
    queryFn: fetchApplicationLinks,
    staleTime: Infinity,
  })
}
