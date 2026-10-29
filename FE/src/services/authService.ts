import { aiService } from "./aiService";
import { demoRoles } from "../constants/mockData";
import type { LoginCredentials } from "../types/auth";
import { apiRequest, setCsrfToken, clearApiOperations } from "./api";
import { useMockApi, postApi } from "./serviceMode";
export interface SessionUser { id: number; name: string; email: string; role: "ADMIN" | "TEACHER" | "STUDENT"; createdAt?: string; birthDate?: string; phone?: string }
export const roleLabels = { ADMIN: "Quản trị viên", TEACHER: "Giáo viên", STUDENT: "Học sinh" };
let currentUser: SessionUser | undefined;
let sessionGeneration = 0;
let restoreRequest: Promise<string | undefined> | undefined;

export const authService = {
  current: () => currentUser,
  clear() { sessionGeneration++; restoreRequest = undefined; currentUser = undefined; setCsrfToken(""); clearApiOperations(); aiService.newConversation(); },
  async login(credentials: LoginCredentials) {
    if (useMockApi) return credentials.password === "123456" ? demoRoles[credentials.email] : undefined;
    const generation = ++sessionGeneration;
    clearApiOperations();
    const session = await postApi<{ user: SessionUser; csrfToken: string }>("auth/login", credentials);
    if (generation !== sessionGeneration) throw new DOMException("Phiên đăng nhập đã thay đổi.", "AbortError");
    currentUser = session.user; aiService.newConversation(); setCsrfToken(session.csrfToken); return roleLabels[session.user.role];
  },
  async restore() {
    if (useMockApi) return undefined;
    if (restoreRequest) return restoreRequest;
    const generation = sessionGeneration;
    const request = apiRequest<{ user: SessionUser; csrfToken: string }>("auth/me").then(session => {
      if (generation !== sessionGeneration) throw new DOMException("Phiên đăng nhập đã thay đổi.", "AbortError");
      currentUser = session.user; setCsrfToken(session.csrfToken); return roleLabels[session.user.role];
    }).finally(() => { if (restoreRequest === request) restoreRequest = undefined; });
    restoreRequest = request;
    return request;
  },
  async logout() { if (!useMockApi) await postApi("auth/logout", {}); this.clear(); },
  async changePassword(oldPassword: string, newPassword: string) { if (useMockApi) throw new Error("Đổi mật khẩu cần backend."); await postApi("auth/change-password", { oldPassword, newPassword }); currentUser = undefined; setCsrfToken(""); window.dispatchEvent(new Event("mathio:session-expired")); },
};
