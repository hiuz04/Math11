import { test, expect, type Page } from "@playwright/test";
async function login(page: Page, role = "Học sinh") {
  await page.goto("/");
  await page.locator(".demo-accounts").getByRole("button", { name: new RegExp(role) }).click();
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.locator(".sidebar")).toBeAttached();
}
async function nav(page: Page, label: string) {
  if (await page.locator(".menu-btn").isVisible()) await page.locator(".menu-btn").click();
  await page.locator(".sidebar nav").getByRole("button", { name: new RegExp("^" + label + "(?: AI)?$") }).click();
}
async function imageBytes(page: Page, size = 1) { return Buffer.from(await page.evaluate(size => { const canvas = document.createElement("canvas"); canvas.width = canvas.height = size; const context = canvas.getContext("2d")!; context.fillStyle = "white"; context.fillRect(0, 0, size, size); return canvas.toDataURL("image/png").split(",")[1]; }, size), "base64"); }

test("essay workflow: wrong hint, gated steps, fresh image retry and persistent history", async ({ page }) => {
  await login(page);
  const pixel = await imageBytes(page);
  await nav(page, "Bài tập");
  await page.locator(".exercise-card").filter({ hasText: "Cấp số cộng và ứng dụng" }).getByRole("button", { name: "Làm bài ngay" }).click();
  await expect(page.getByRole("heading", { name: "Đề bài", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Upload ảnh", exact: true }).first().click();
  await page.locator('input[aria-label="Upload ảnh bài làm"]').setInputFiles({ name: "original.png", mimeType: "image/png", buffer: pixel });
  await expect(page.getByAltText("Ảnh bài làm đã chọn")).toBeVisible();
  await expect(page.locator('input[capture="environment"]')).toHaveCount(1);
  await page.getByRole("button", { name: "Nộp bài", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Bước 1", exact: true })).toBeVisible();
  await page.getByLabel("Câu trả lời của bạn").fill("wrong");
  await page.locator("main").getByRole("button", { name: "Kiểm tra", exact: true }).click();
  await expect(page.getByText("Gợi ý", { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Bước 2", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Tiếp tục", exact: true })).toHaveCount(0);
  for (const answer of ["2+(10-1)*3", "27", "29"]) {
    await page.getByLabel("Câu trả lời của bạn").fill(answer);
    await page.locator("main").getByRole("button", { name: "Kiểm tra", exact: true }).click();
    await page.getByRole("button", { name: "Tiếp tục", exact: true }).click();
  }
  await page.getByRole("button", { name: "Làm lại bài", exact: true }).click();
  const file = page.locator('input[aria-label="Upload ảnh bài làm"]');
  await file.setInputFiles({ name: "renamed.png", mimeType: "image/png", buffer: pixel });
  await expect(page.getByRole("alert")).toContainText("Ảnh đã nộp trước đó");
  // Different real image bytes, not just a different filename.
  const newPixel = await page.evaluate(async () => { const c = document.createElement("canvas"); c.width = c.height = 2; const x = c.getContext("2d")!; x.fillStyle = "white"; x.fillRect(0, 0, 2, 2); return c.toDataURL("image/png").split(",")[1]; });
  await file.setInputFiles({ name: "retry.png", mimeType: "image/png", buffer: Buffer.from(newPixel, "base64") });
  await page.getByRole("button", { name: "Nộp lại bài", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Chi tiết bài làm", exact: true }).first()).toBeVisible();
  await expect(page.getByText("Cần chỉnh sửa", { exact: true })).toBeVisible();
  await expect(page.getByAltText("Ảnh bài làm đã nộp")).toHaveCount(2);
  await page.reload(); await login(page);
  await nav(page, "Bài tập"); await page.getByRole("button", { name: "Lịch sử học tập", exact: true }).click();
  await expect(page.locator("tbody tr")).toHaveCount(3);
  await page.getByRole("button", { name: "Tự luận", exact: true }).click();
  await expect(page.locator("tbody tr")).toHaveCount(2);
  await page.getByRole("button", { name: "Xem", exact: true }).first().click();
  await expect(page.getByAltText("Ảnh bài làm đã nộp")).toHaveCount(2);
});

test("teacher generates all requested questions and saves a 16-question matrix exam", async ({ page }) => {
  await login(page, "Giáo viên");
  await nav(page, "Tạo câu hỏi bằng AI");
  await page.getByLabel("Mức độ nhận thức").selectOption("APPLICATION");
  await page.getByLabel("Loại câu hỏi").selectOption("ESSAY");
  await page.getByRole("button", { name: "Tăng số câu" }).click();
  await page.getByRole("button", { name: "Tăng số câu" }).click();
  await page.locator(".generator-form").getByRole("button", { name: "Tạo câu hỏi bằng AI" }).click();
  await expect(page.locator(".generated-question")).toHaveCount(7);
  await page.getByRole("button", { name: "Thêm 7 câu vào ngân hàng" }).click();
  await expect(page.locator("tbody tr")).toHaveCount(10);
  await page.getByRole("button", { name: "Tạo đề", exact: true }).click();
  await expect(page.getByText("Tổng số câu:")).toContainText("16");
  await page.getByRole("button", { name: "Tạo đề", exact: true }).click();
  await expect(page.locator(".generated-question")).toHaveCount(16);
  await page.getByRole("button", { name: "Lưu đề", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Đề đã lưu" })).toBeVisible();
  await nav(page, "Bài giảng"); await page.getByRole("button", { name: "Thêm mới" }).click();
  await page.locator(".tiptap").fill("Nội dung bài giảng");
  await page.getByRole("button", { name: "Bold", exact: true }).click();
  await page.getByRole("button", { name: "Formula", exact: true }).click();
  await page.getByLabel("LaTeX").fill("u_n = u_1 + (n-1)d");
  await page.getByRole("button", { name: "Chèn công thức" }).click();
  await expect(page.locator(".tiptap .katex")).toBeVisible();
  await page.getByRole("button", { name: "Lưu nháp", exact: true }).click();
  await expect(page.locator(".toast")).toContainText("Đã lưu bản nháp");
});

test("admin configuration validation, persistence, masked key and prompt restore", async ({ page }) => {
  await login(page, "Admin"); await nav(page, "Hệ thống");
  await expect(page.getByRole("heading", { name: "Gemini API", exact: true })).toBeVisible();
  await page.getByLabel("API Key", { exact: true }).fill("test-key-only");
  await page.getByRole("button", { name: "Hiện", exact: true }).click();
  await expect(page.getByLabel("API Key", { exact: true })).toHaveAttribute("type", "text");
  await page.getByLabel("Temperature", { exact: true }).fill("3");
  await page.getByRole("button", { name: "Lưu cấu hình", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Temperature");
  await page.getByLabel("Temperature", { exact: true }).fill("0.4");
  await page.getByLabel("Enable Gemini").uncheck();
  await page.getByRole("button", { name: "Lưu cấu hình", exact: true }).click();
  await expect(page.getByLabel("API Key", { exact: true })).toHaveValue("");
  await page.getByRole("button", { name: "Kiểm tra kết nối", exact: true }).click();
  await expect(page.getByRole("status").last()).toContainText("Gemini đang tắt");
  await page.getByLabel("Essay Evaluation Prompt", { exact: true }).fill("Test custom prompt");
  await page.getByRole("button", { name: "Lưu Prompt", exact: true }).click();
  await nav(page, "Tổng quan"); await nav(page, "Hệ thống");
  await expect(page.getByLabel("Essay Evaluation Prompt", { exact: true })).toHaveValue("Test custom prompt");
  await expect(page.getByLabel("Enable Gemini")).not.toBeChecked();
  await page.getByRole("button", { name: "Khôi phục mặc định", exact: true }).click();
  await page.getByRole("button", { name: "Khôi phục", exact: true }).click();
  await expect(page.getByLabel("System Prompt", { exact: true })).toHaveValue(/Chỉ hiển thị một bước/);
  expect(await page.evaluate(() => Object.values(localStorage).join(""))).not.toContain("test-key-only");
});

for (const width of [1440, 768, 390]) test(`responsive and old page navigation at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 });
  const errors: string[] = []; page.on("pageerror", e => errors.push(e.message));
  await login(page);
  for (const label of ["Khóa học", "Bài giảng", "Bài tập", "Kiểm tra", "AI Tutor", "Tiến độ học tập", "Kết quả", "Tổng quan"]) {
    await nav(page, label);
    expect(await page.locator(".app-main").isVisible()).toBeTruthy();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  }
  await nav(page, "Bài tập");
  await page.locator(".exercise-card").filter({ hasText: "Cấp số cộng và ứng dụng" }).getByRole("button", { name: "Làm bài ngay" }).click();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.getByRole("button", { name: "Upload ảnh", exact: true }).first().click();
  await expect(page.getByRole("button", { name: "Chụp ảnh bài làm", exact: true })).toBeVisible();
  await page.screenshot({ path: `artifacts/qa/essay-${width}.png`, fullPage: true, animations: "disabled" });
  await login(page, "Giáo viên");
  for (const label of ["Tổng quan", "Bài giảng", "Ngân hàng câu hỏi", "Học sinh", "Tạo câu hỏi bằng AI", "Kết quả học tập"]) {
    await nav(page, label);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  }
  await nav(page, "Ngân hàng câu hỏi"); await page.getByRole("button", {name:"Tạo đề", exact:true}).click();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.screenshot({path:`artifacts/qa/exam-${width}.png`, fullPage:true, animations:"disabled"});
  await login(page, "Admin");
  for (const label of ["Tổng quan", "Người dùng", "Quản lý khóa học", "Quản lý nội dung", "Thống kê", "Hệ thống"]) {
    await nav(page, label);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  }
  await page.screenshot({path:`artifacts/qa/admin-${width}.png`, fullPage:true, animations:"disabled"});
  expect(errors).toEqual([]);
});
