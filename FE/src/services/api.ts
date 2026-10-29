const env = (import.meta as ImportMeta & { env?: Record<string, string | undefined> }).env;
/** Default to the same-origin proxy; preserve a configured WAR context and include /api once. */
export function resolveApiBaseUrl(config: Record<string, string | undefined> = {}) {
  const configured = config.VITE_API_URL?.trim() || config.VITE_API_BASE_URL?.trim() || "/api";
  const url = new URL(configured, "http://math11.local");
  const apiPath = url.pathname.replace(/\/+$/, "").replace(/(?:\/api)+$/, "") + "/api";
  return /^https?:\/\//i.test(configured) ? url.origin + apiPath : apiPath;
}
export const API_BASE_URL = resolveApiBaseUrl(env);

export class ApiError extends Error {
  public code: string;
  public kind: "validation" | "authentication" | "authorization" | "conflict" | "server" | "network";
  constructor(public status: number, public body: string) {
    const messages: Record<number, string> = { 0: "Không kết nối được máy chủ. Hãy kiểm tra mạng rồi thử lại.", 400: "Dữ liệu không hợp lệ.", 401: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", 403: "Bạn không có quyền thực hiện thao tác này.", 404: "Không tìm thấy dữ liệu.", 409: "Dữ liệu đã thay đổi hoặc thao tác đang được xử lý.", 413: "Tệp hoặc yêu cầu quá lớn.", 422: "Vui lòng kiểm tra dữ liệu đã nhập.", 429: "Quá nhiều yêu cầu. Vui lòng thử lại sau.", 500: "Máy chủ gặp lỗi. Vui lòng thử lại sau." };
    let message = messages[status] || (status === 502 ? "Máy chủ trả phản hồi không hợp lệ." : "Dịch vụ tạm thời không khả dụng.");
    let code = `HTTP_${status}`;
    try { const error = JSON.parse(body).error; if (typeof error?.message === "string" && !/<[^>]+>/.test(error.message)) message = error.message; if (typeof error?.code === "string") code = error.code; } catch { /* Never display proxy HTML or stack traces. */ }
    super(message);
    this.code = code;
    this.kind = status === 0 ? "network" : status === 401 ? "authentication" : status === 403 ? "authorization" : status === 409 ? "conflict" : [400, 413, 422].includes(status) ? "validation" : "server";
  }
}

/** This client serves JSON endpoints; file/image URLs are rendered by the browser. */
async function readJson<T>(response: Response): Promise<T> {
  const mediaType = response.headers.get("Content-Type")?.split(";", 1)[0].trim().toLowerCase();
  if (!mediaType || !(mediaType === "application/json" || /^application\/.+\+json$/.test(mediaType))) throw new ApiError(502, "");
  try { return await response.json() as T; }
  catch (error) { if (error instanceof DOMException && error.name === "AbortError") throw error; throw new ApiError(502, ""); }
}

let csrfToken = "";
let csrfRequest: Promise<void> | undefined;
export function setCsrfToken(token: string) { csrfToken = token; }
const operations = new Map<string, { key: string; pending?: Promise<unknown> }>();
let operationGeneration = 0;
/** Explicit retries reuse the key after an ambiguous transport/server failure. */
export async function apiOperation<T>(endpoint: string, fingerprint: string, options: RequestInit): Promise<T> {
  const identity = `${endpoint}:${fingerprint}`;
  const generation = operationGeneration;
  let operation = operations.get(identity);
  if (!operation) { operation = { key: crypto.randomUUID() }; operations.set(identity, operation); }
  if (operation.pending) return operation.pending as Promise<T>;
  const headers = new Headers(options.headers); headers.set("Idempotency-Key", operation.key);
  operation.pending = apiRequest<T>(endpoint, { ...options, headers }).then(value => {
    if (generation !== operationGeneration) throw new DOMException("Phiên thao tác đã thay đổi.", "AbortError");
    if (operations.get(identity) === operation) operations.delete(identity);
    return value;
  }).catch(error => {
    if (operations.get(identity) === operation && error instanceof ApiError && error.status > 0 && error.status < 500 && ![409, 429].includes(error.status)) operations.delete(identity);
    throw error;
  }).finally(() => { operation.pending = undefined; });
  return operation.pending as Promise<T>;
}
export function clearApiOperations() { operationGeneration++; operations.clear(); csrfRequest = undefined; }
async function ensureCsrf() {
  if (csrfToken) return;
  if (csrfRequest) return csrfRequest;
  const generation = operationGeneration;
  const request = (async () => {
    let response: Response;
    try { response = await fetch(API_BASE_URL + "/auth/csrf", { credentials: "include", cache: "no-store", headers: { Accept: "application/json" } }); }
    catch { throw new ApiError(0, ""); }
    if (!response.ok) throw new ApiError(response.status, await response.text());
    const body = await readJson<{ csrfToken?: unknown } | null>(response);
    const token = body?.csrfToken;
    if (generation !== operationGeneration) throw new DOMException("Phiên thao tác đã thay đổi.", "AbortError");
    if (typeof token !== "string" || !token) throw new ApiError(500, "");
    csrfToken = token;
  })().finally(() => { if (csrfRequest === request) csrfRequest = undefined; });
  csrfRequest = request;
  await request;
}

export async function apiRequest<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const generation = operationGeneration;
  const headers = new Headers(options.headers);
  if (!headers.has("Accept")) headers.set("Accept", "application/json");
  const mutation = !["GET", "HEAD", "OPTIONS"].includes((options.method || "GET").toUpperCase());
  if (mutation) { await ensureCsrf(); headers.set("X-CSRF-Token", csrfToken); if (!headers.has("Idempotency-Key")) headers.set("Idempotency-Key", crypto.randomUUID()); }
  if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  if (options.body instanceof FormData) headers.delete("Content-Type");
  options.signal?.throwIfAborted();
  if (generation !== operationGeneration) throw new DOMException("Phiên thao tác đã thay đổi.", "AbortError");
  let response: Response;
  try { response = await fetch(API_BASE_URL + "/" + endpoint.replace(/^\/+/, ""), { ...options, credentials: "include", cache: "no-store", headers }); }
  catch (error) { if (options.signal?.aborted || error instanceof DOMException && error.name === "AbortError") throw error; throw new ApiError(0, ""); }
  if (generation !== operationGeneration) throw new DOMException("Phiên thao tác đã thay đổi.", "AbortError");
  if (!response.ok) {
    if (response.status === 401 && generation === operationGeneration) { csrfToken = ""; if (typeof window !== "undefined") window.dispatchEvent(new Event("mathio:session-expired")); }
    throw new ApiError(response.status, await response.text());
  }
  if (response.status === 204 || response.status === 205 || options.method?.toUpperCase() === "HEAD") return undefined as T;
  const body = await readJson<T>(response);
  options.signal?.throwIfAborted();
  if (generation !== operationGeneration) throw new DOMException("Phiên thao tác đã thay đổi.", "AbortError");
  return body;
}
