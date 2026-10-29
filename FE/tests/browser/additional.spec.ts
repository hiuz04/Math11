import { test, expect, type Page } from "@playwright/test";
async function login(page: Page, role: string) {
  await page.goto("/"); await page.locator(".demo-accounts").getByRole("button", { name: new RegExp(role) }).click();
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click(); await expect(page.locator(".sidebar")).toBeVisible();
}
test("manual question creation persists cognitive level and generated questions can be edited", async ({ page }) => {
  await login(page, "Giáo viên");
  await page.locator(".sidebar nav").getByRole("button", { name: "Ngân hàng câu hỏi", exact: true }).click();
  await page.getByRole("button", { name: "Thêm mới", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "Tạo câu hỏi", exact: true });
  await dialog.getByLabel("Tên câu hỏi").fill("Bài tự luận mới");
  await dialog.getByLabel("Loại câu hỏi").selectOption("ESSAY");
  await dialog.getByLabel("Mức độ nhận thức").selectOption("APPLICATION");
  await dialog.locator(".tiptap").fill("Trình bày cách tính số hạng tổng quát.");
  await dialog.getByLabel("Đáp án / hướng dẫn chấm").fill("Áp dụng công thức u_n = u_1 + (n-1)d và giải thích từng đại lượng.");
  await dialog.getByRole("button", { name: "Lưu câu hỏi", exact: true }).click();
  await expect(page.locator("tbody tr").filter({ hasText: "Bài tự luận mới" })).toContainText("Vận dụng");
  await page.locator(".sidebar nav").getByRole("button", { name: "Tạo câu hỏi bằng AI", exact: true }).click();
  await page.locator(".generator-form").getByRole("button", { name: "Tạo câu hỏi bằng AI", exact: true }).click();
  await page.locator(".generated-question").first().getByRole("button", { name: "Chỉnh sửa" }).click();
  await page.getByRole("dialog", { name: "Chỉnh sửa câu hỏi", exact: true }).locator(".tiptap").fill("Nội dung đã chỉnh sửa");
  await page.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  await expect(page.locator(".generated-question").first()).toContainText("Nội dung đã chỉnh sửa");
  await page.getByRole("button", { name: "Thêm 5 câu vào ngân hàng", exact: true }).click();
  await expect(page.locator("tbody tr")).toHaveCount(9);
});
test("correct essay, demo quiz and AI conversation appear in learning history", async ({ page }) => {
  await login(page, "Học sinh");
  await page.locator(".sidebar nav").getByRole("button", { name: "Bài tập", exact: true }).click();
  await page.locator(".exercise-card").filter({ hasText: "Cấp số cộng và ứng dụng" }).getByRole("button", { name: "Làm bài ngay" }).click();
  await page.getByLabel("Bài làm", { exact: true }).fill("u10 = 29");
  await page.getByRole("button", { name: "Nộp bài", exact: true }).click();
  await expect(page.getByText("Hoàn thành", { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Các bước AI đã hướng dẫn" })).toHaveCount(0);
  await page.locator(".sidebar nav").getByRole("button", { name: "Kiểm tra", exact: true }).click();
  await page.locator(".quiz-top").getByRole("button", { name: "Nộp bài", exact: true }).click();
  await page.locator(".modal-card").getByRole("button", { name: "Nộp bài", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Kết quả bài luyện tập" })).toBeVisible();
  await page.locator(".sidebar nav").getByRole("button", { name: "AI Tutor AI", exact: true }).click();
  await page.locator(".composer-box textarea").fill("π/3");
  await page.locator(".send-btn").click();
  await expect(page.locator(".messages")).toContainText("Hãy kiểm tra lại giá trị sin");
  await page.locator(".sidebar nav").getByRole("button", { name: "Bài tập", exact: true }).click();
  await page.getByRole("button", { name: "Lịch sử học tập", exact: true }).click();
  await expect(page.locator("tbody tr")).toHaveCount(3);
  await page.getByRole("button", { name: "Trắc nghiệm", exact: true }).click();
  await expect(page.locator("tbody tr")).toHaveCount(1);
});
