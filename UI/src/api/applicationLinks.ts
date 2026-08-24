import { useQuery } from "@tanstack/react-query"
import { httpClient } from "@/api/httpClient"

export interface ApplicationLinks {
  centralUrl: string | null
  innoventaUrl: string | null
  kiwiUrl: string | null
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
