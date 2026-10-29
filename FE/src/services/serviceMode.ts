import { apiOperation } from "./api";
import { readSetting } from "./storage";
export const useMockApi = (import.meta.env?.VITE_USE_MOCK_API ?? (typeof process !== "undefined" ? process.env.VITE_USE_MOCK_API : undefined)) === "true";
export function assertMockAiEnabled() {
  if (useMockApi && !readSetting("gemini", { enabled: true }).enabled) throw new Error("Gemini đang tắt trong cấu hình hệ thống.");
}
export async function postApi<T>(path: string, payload: unknown): Promise<T> {
  const body = JSON.stringify(payload);
  return apiOperation<T>(path, body, { method: "POST", body });
}
export const errorMessage = (error: unknown) => error instanceof Error ? error.message : "Có lỗi xảy ra. Vui lòng thử lại.";
