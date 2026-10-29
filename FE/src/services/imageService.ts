import type { SubmissionContent } from "../types/lms";
import { useMockApi } from "./serviceMode";
import { apiOperation } from "./api";
export async function readSubmissionImage(file: File): Promise<SubmissionContent> {
  if (!/\.(jpe?g|png|webp)$/i.test(file.name) || !["image/jpeg", "image/png", "image/webp"].includes(file.type)) throw new Error("Chỉ hỗ trợ ảnh JPG, JPEG, PNG hoặc WEBP.");
  if (file.size > 8 * 1024 * 1024) throw new Error("Ảnh phải nhỏ hơn 8 MB.");
  if (!file.size) throw new Error("Tệp ảnh rỗng.");
  const bitmap = await createImageBitmap(file).catch(() => { throw new Error("Tệp không phải ảnh hợp lệ hoặc ảnh bị hỏng."); });
  const oversized = bitmap.width * bitmap.height > 30_000_000;
  bitmap.close();
  if (oversized) throw new Error("Kích thước ảnh không được vượt quá 30 triệu pixel.");
  const bytes = await file.arrayBuffer();
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  const imageHash = Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, "0")).join("");
  if (!useMockApi) return { image: URL.createObjectURL(file), imageFile: file, imageName: file.name, imageHash };
  const image = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader(); reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error("Không đọc được ảnh.")); reader.readAsDataURL(file);
  });
  return { image, imageName: file.name, imageHash };
}
export async function submitContent<T>(endpoint: string, fields: Record<string, unknown>, content: SubmissionContent): Promise<T> {
  const { imageFile, image, ...metadata } = content;
  const payload = { ...fields, content: { ...metadata, ...(imageFile ? {} : image ? { image } : {}) } };
  const fingerprint = JSON.stringify(payload) + (imageFile ? `:${content.imageHash}` : "");
  if (imageFile) {
    const body = new FormData(); body.append("payload", JSON.stringify(payload)); body.append("image", imageFile);
    return apiOperation<T>(endpoint, fingerprint, { method: "POST", body });
  }
  return apiOperation<T>(endpoint, fingerprint, { method: "POST", body: JSON.stringify(payload) });
}
