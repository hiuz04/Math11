import type { Chapter } from "../types/course";
import type { Lesson } from "../types/lesson";
import type { Exercise } from "../types/exercise";
import type { UserRow } from "../types/user";
import type { IconName } from "../types/icon";

export const stats = [
  { icon: "play" as IconName, value: "12", unit: "bài học", label: "Đã hoàn thành", tone: "blue", note: "+3 tuần này" },
  { icon: "file" as IconName, value: "38", unit: "bài tập", label: "Đã luyện tập", tone: "purple", note: "+8 tuần này" },
  { icon: "award" as IconName, value: "8.5", unit: "điểm", label: "Điểm trung bình", tone: "orange", note: "Tăng 0.6" },
  { icon: "clock" as IconName, value: "16", unit: "giờ", label: "Tổng thời gian học", tone: "green", note: "+2.5 giờ" },
];

export const chapters: Chapter[] = [
  ["01","Hàm số lượng giác và phương trình lượng giác","12 bài học","24 bài tập",100],
  ["02","Dãy số – Cấp số cộng – Cấp số nhân","12 bài học","20 bài tập",65],
  ["03","Giới hạn – Hàm số liên tục","10 bài học","18 bài tập",20],
  ["04","Đường thẳng và mặt phẳng trong không gian","14 bài học","26 bài tập",0],
  ["05","Quan hệ song song trong không gian","9 bài học","16 bài tập",0],
];

export const managementTitles: Record<string, [string, string]> = {
    lessons:["Quản lý bài giảng","Tạo và quản lý nội dung học tập Toán 11."],
    questions:["Ngân hàng câu hỏi","Quản lý câu hỏi và xây dựng bộ đề chất lượng."],
    students:["Học sinh","Theo dõi tiến độ và hỗ trợ từng học sinh."],
    users:["Quản lý người dùng","Quản lý tài khoản học sinh, giáo viên và quản trị viên."],
    generator:["Tạo câu hỏi bằng AI","Tạo bộ câu hỏi chất lượng chỉ trong vài giây."],
    system:["Cài đặt hệ thống","Quản lý cấu hình và bảo mật nền tảng."],
  };

export const demoAccounts: string[][] = [
    ["Học sinh", "student@demo.vn", "NA"],
    ["Giáo viên", "teacher@demo.vn", "NL"],
    ["Admin", "admin@demo.vn", "TM"],
  ];

export const demoRoles: Record<string, string> = { "student@demo.vn": "Học sinh", "teacher@demo.vn": "Giáo viên", "admin@demo.vn": "Quản trị viên" };

export const mockLessons: Lesson[] = [
    ["Bài 1 – Góc lượng giác","18 phút",true],["Bài 2 – Giá trị lượng giác","22 phút",true],["Bài 3 – Hàm số lượng giác","26 phút",false],["Bài 4 – Phương trình lượng giác cơ bản","24 phút",false],["Bài 5 – Một số phương trình lượng giác thường gặp","30 phút",false],
  ];

export const mockExercises: Exercise[] = [
      ["Hàm số lượng giác","Chương 1","Trắc nghiệm","Dễ",15,90,"green"],
      ["Phương trình lượng giác cơ bản","Chương 1","Trắc nghiệm","Trung bình",12,70,"orange"],
      ["Cấp số cộng và ứng dụng","Chương 2","Tự luận","Trung bình",10,0,"orange"],
      ["Bài toán thực tế về cấp số nhân","Chương 2","Tự luận","Khó",8,0,"red"],
      ["Giới hạn hữu hạn của dãy số","Chương 3","Trắc nghiệm","Dễ",15,30,"green"],
      ["Ôn tập tổng hợp Chương 2","Chương 2","Trắc nghiệm","Khó",20,0,"red"],
    ];

export const mockUsers: UserRow[] = [["USR001","Nguyễn Minh Anh","student@demo.vn","Học sinh","Hoạt động"],["USR002","Trần Hoàng Nam","nam.tran@demo.vn","Học sinh","Hoạt động"],["USR003","Nguyễn Thị Lan","teacher@demo.vn","Giáo viên","Hoạt động"],["USR004","Lê Thu Trang","trang.le@demo.vn","Học sinh","Đã khóa"]];
