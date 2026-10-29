import { defaultPrompts } from "../constants/lms";
import type { GeminiConfig, Prompts } from "../types/lms";
import { readSetting, writeSetting } from "./storage";
import { apiRequest } from "./api";
import { postApi, useMockApi } from "./serviceMode";
const initial: GeminiConfig = { model: "gemini-2.5-flash", temperature: 0.2, maxOutputTokens: 2048, enabled: true, hasApiKey: false };
function validate(config: GeminiConfig) {
  if (!config.model.trim()) throw new Error("Hãy nhập tên model.");
  if (!Number.isFinite(config.temperature) || config.temperature < 0 || config.temperature > 2) throw new Error("Temperature phải từ 0 đến 2.");
  if (!Number.isInteger(config.maxOutputTokens) || config.maxOutputTokens < 1 || config.maxOutputTokens > 65536) throw new Error("Max Output Tokens phải từ 1 đến 65536.");
}
export const adminConfigService = {
  async getGemini(): Promise<GeminiConfig> { return useMockApi ? readSetting("gemini", initial) : apiRequest("admin/config/gemini"); },
  async saveGemini(config: GeminiConfig, apiKey?: string): Promise<GeminiConfig> {
    validate(config);
    if (!useMockApi) return postApi("admin/config/gemini", { model: config.model, temperature: config.temperature, maxOutputTokens: config.maxOutputTokens, enabled: config.enabled, ...(apiKey ? { apiKey } : {}) });
    const next = { ...config, hasApiKey: false }; writeSetting("gemini", next); return next;
  },
  async testConnection(config: GeminiConfig, apiKey?: string): Promise<{ ok: boolean; message: string }> {
    validate(config);
    if (!config.enabled) return { ok: false, message: "Gemini đang tắt. Hãy bật trước khi kiểm tra kết nối." };
    if (!useMockApi) return postApi("admin/config/gemini/test", { ...config, ...(apiKey ? { apiKey } : {}) });
    return { ok: false, message: "Cấu hình hợp lệ. Mock không gọi Gemini và chưa thể xác nhận API key. Cần Java Servlet để kiểm tra kết nối." };
  },
  async getPrompts(): Promise<Prompts> { return useMockApi ? readSetting("prompts", defaultPrompts) : apiRequest("admin/config/prompts"); },
  async savePrompts(prompts: Prompts): Promise<void> {
    if (Object.values(prompts).some(p => !p.trim())) throw new Error("Tất cả Prompt đều cần nội dung.");
    if (!useMockApi) return postApi("admin/config/prompts", prompts);
    writeSetting("prompts", prompts);
  },
};
