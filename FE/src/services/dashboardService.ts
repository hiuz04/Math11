import type { IconName } from "../types/icon";
import { apiRequest } from "./api";
import { useMockApi } from "./serviceMode";
export interface DashboardStatistic { icon: IconName; value: string; label: string; note: string; tone: "blue" | "purple" | "orange" | "green" }
export const dashboardService = {
  getMockStatistics(role: string): DashboardStatistic[] {
    return role === "Giáo viên" ? [
      { icon: "users", value: "128", label: "Học sinh", note: "+8 tuần này", tone: "blue" },
      { icon: "book", value: "24", label: "Bài giảng", note: "+4 tuần này", tone: "purple" },
      { icon: "file", value: "356", label: "Câu hỏi", note: "+36 tuần này", tone: "orange" },
      { icon: "target", value: "42", label: "Bài tập", note: "6 đang mở", tone: "green" },
    ] : [
      { icon: "users", value: "8.642", label: "Học sinh", note: "+12,4%", tone: "blue" },
      { icon: "user", value: "186", label: "Giáo viên", note: "+6 tháng này", tone: "purple" },
      { icon: "book", value: "328", label: "Bài giảng", note: "+18 tháng này", tone: "orange" },
      { icon: "file", value: "4.892", label: "Bài tập", note: "+9,1%", tone: "green" },
    ];
  },
  async getStatistics(role: string): Promise<DashboardStatistic[]> { return useMockApi ? this.getMockStatistics(role) : apiRequest(`dashboard?role=${encodeURIComponent(role)}`); },
};
