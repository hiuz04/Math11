import { mockLessons } from "../constants/mockData";
import { readSetting, writeSetting } from "./storage";
import { postApi, useMockApi } from "./serviceMode";
import { apiRequest } from "./api";
import { selection, type CatalogLesson } from "./lmsService";

// Mock adapter preserves current rendering and interaction timing.
export const lessonService = {
  getLessons: () => useMockApi ? mockLessons.map(item => [...item] as typeof item) : [],
  getDraft: () => useMockApi ? readSetting("lesson-draft", "<p>Nhập nội dung bài giảng tại đây...</p>") : "<p></p>",
  async saveContent(content: string, published = false, metadata: Partial<CatalogLesson> = {}): Promise<void> {
    if (!useMockApi) { const result = await postApi<{ id: number }>(selection.editLessonId ? `lessons/${selection.editLessonId}` : "lessons", { ...metadata, content, published }); selection.editLessonId = result.id; return; }
    writeSetting("lesson-draft", content);
    if (published) writeSetting("published-lessons", [...readSetting<string[]>("published-lessons", []), content]);
  },
};
