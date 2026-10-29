import type { IconName } from "../types/icon";

export const studentNav = [
  { id: "dashboard", label: "Tổng quan", icon: "grid" as IconName },
  { id: "courses", label: "Khóa học", icon: "book" as IconName },
  { id: "lessons", label: "Bài giảng", icon: "play" as IconName },
  { id: "exercises", label: "Bài tập", icon: "file" as IconName },
  { id: "quiz", label: "Kiểm tra", icon: "target" as IconName },
  { id: "ai", label: "AI Tutor", icon: "spark" as IconName },
  { id: "progress", label: "Tiến độ học tập", icon: "chart" as IconName },
  { id: "results", label: "Kết quả", icon: "award" as IconName },
];

export const teacherNav = [
  { id: "teacher", label: "Tổng quan", icon: "grid" as IconName },
  { id: "lessons", label: "Bài giảng", icon: "book" as IconName },
  { id: "questions", label: "Ngân hàng câu hỏi", icon: "file" as IconName },
  { id: "students", label: "Học sinh", icon: "users" as IconName },
  { id: "generator", label: "Tạo câu hỏi bằng AI", icon: "spark" as IconName },
  { id: "progress", label: "Kết quả học tập", icon: "chart" as IconName },
];

export const adminNav = [
  { id: "admin", label: "Tổng quan", icon: "grid" as IconName },
  { id: "users", label: "Người dùng", icon: "users" as IconName },
  { id: "courses", label: "Quản lý khóa học", icon: "book" as IconName },
  { id: "lessons", label: "Quản lý nội dung", icon: "layers" as IconName },
  { id: "progress", label: "Thống kê", icon: "chart" as IconName },
  { id: "system", label: "Hệ thống", icon: "settings" as IconName },
];
