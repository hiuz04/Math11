import { apiRequest } from "./api"
export const PAGE_SIZE = 20
export async function allPages<T>(
  endpoint: string,
  signal?: AbortSignal,
): Promise<T[]> {
  const result: T[] = []
  for (let offset = 0; offset <= 1_000_000; offset += 100) {
    const rows = await apiRequest<T[]>(
      endpoint +
        (endpoint.includes("?") ? "&" : "?") +
        new URLSearchParams({ offset: String(offset), limit: "100" }),
      { signal },
    )
    result.push(...rows)
    if (rows.length < 100) return result
  }
  throw new Error("Danh sách quá lớn. Hãy thu hẹp bộ lọc.")
}
