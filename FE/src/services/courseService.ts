import { chapters } from "../constants/mockData";
import { useMockApi } from "./serviceMode";
import { lmsService } from "./lmsService";
import type { Chapter } from "../types/course";

// Mock adapter preserves current rendering and interaction timing.
export const courseService = {
  getCourses: (): Chapter[] => useMockApi ? chapters.map(item => [...item] as typeof item) : [],
  async list(): Promise<Chapter[]> { return useMockApi ? this.getCourses() : (await lmsService.chapters()).map(c => [String(c.position).padStart(2, "0"), c.title, `${c.lessonCount} bài học`, `${c.exerciseCount} bài tập`, c.progress]); },
};
