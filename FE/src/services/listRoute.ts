export function readListRoute(key: string) {
  return (
    new URLSearchParams(window.location.hash.split("?")[1] || "").get(
      "list_" + key,
    ) || ""
  )
}
export function readListPage() {
  const n = Number(readListRoute("page"))
  return Number.isSafeInteger(n) && n >= 0 && n <= 50000 ? n : 0
}
export function saveListRoute(values: Record<string, string | number>) {
  const [path, query] = window.location.hash.split("?")
  const params = new URLSearchParams(query)
  Object.entries(values).forEach(([key, value]) => {
    if (value) params.set("list_" + key, String(value))
    else params.delete("list_" + key)
  })
  window.history.replaceState(
    null,
    "",
    path + (params.size ? "?" + params : ""),
  )
}
