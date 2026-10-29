import { mockUsers } from "../constants/mockData";
import { apiRequest } from "./api";
import { useMockApi, postApi } from "./serviceMode";
import { roleLabels, type SessionUser } from "./authService";
export interface UserRecord extends SessionUser { active: boolean }

// Mock adapter preserves current rendering and interaction timing.
export const userService = {
  getUsers: () => useMockApi ? mockUsers.map(item => [...item] as typeof item) : [],
  list: () => apiRequest<UserRecord[]>("users"),
  save: (user: Partial<UserRecord> & { password?: string }) => postApi<UserRecord>(user.id ? `users/${user.id}` : "users", user),
  remove: (id: number) => apiRequest<void>(`users/${id}`, { method: "DELETE" }),
  row: (u: UserRecord) => [String(u.id), u.name, u.email, roleLabels[u.role], u.active ? "Hoạt động" : "Đã khóa"],
};
