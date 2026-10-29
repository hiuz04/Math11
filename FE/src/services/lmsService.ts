import { apiRequest } from "./api";
import { postApi, useMockApi } from "./serviceMode";
import type { Question } from "../types/lms";
export interface CatalogChapter { id: number; title: string; position: number; lessonCount: number; exerciseCount: number; progress: number }
export interface CatalogLesson { id: number; title: string; chapter: string; chapterId: number; courseId: number; description: string; durationSeconds: number; completed: boolean; published: boolean; updatedAt: string; content?: string; objectives?: string; videoUrl?: string; contentType?: string; elapsedSeconds?: number }
type CatalogLessonWire = Omit<CatalogLesson, "completed"> & { completed: boolean | 0 | 1 };
function lessonFromApi(row: CatalogLessonWire): CatalogLesson {
  if (![true, false, 0, 1].includes(row.completed)) throw new Error("Dữ liệu tiến độ bài học không hợp lệ.");
  return { ...row, completed: row.completed === true || row.completed === 1 };
}
export interface CourseRecord { id: number; title: string; description: string; teacherId: number; published: boolean; createdAt: string }
export interface ExerciseRecord extends Partial<Question> { id: number; title: string; chapter: string; kind: "essay" | "quiz"; durationSeconds?: number | null; questionCount: number; status: "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED"; bestScore?: number | null }
export interface Activity { title: string; status: string; date: string; score?: number; type: string; name?: string }
export interface StudentAnalytics { name: string; completedLessons: number; completedExercises: number; averageScore: number; elapsedHours: number; chapters: { title: string; progress: number; completedLessons: number; totalLessons: number }[]; activities: Activity[]; scores: { score: number; date: string }[]; continueLesson?: { id: number; title: string; chapterId: number } }
export interface ManagementAnalytics { activities: Activity[]; trend: { month: string; value: number }[]; distribution: { label: string; count: number }[]; aiUsage?: number }
import { selection } from "./routeState";
export { selection } from "./routeState";
export const lmsService = {
  chapters: (signal?: AbortSignal) => apiRequest<CatalogChapter[]>(`chapters${selection.courseId ? `?courseId=${selection.courseId}` : ""}`, { signal }),
  topics: (signal?: AbortSignal) => apiRequest<{ id: number; chapterId: number; title: string }[]>("topics", { signal }),
  courses: (signal?: AbortSignal) => apiRequest<CourseRecord[]>("courses", { signal }),
  allLessons: async (signal?: AbortSignal) => (await apiRequest<CatalogLessonWire[]>("lessons", { signal })).map(lessonFromApi),
  lessons: async (signal?: AbortSignal) => { const course = selection.courseId; const rows = (await apiRequest<CatalogLessonWire[]>(`lessons${selection.chapterId ? `?chapterId=${selection.chapterId}` : ""}`, { signal })).map(lessonFromApi); return course ? rows.filter(l=>l.courseId===course) : rows; },
  lesson: (id: number, signal?: AbortSignal) => apiRequest<CatalogLessonWire>(`lessons/${id}`, { signal }).then(lessonFromApi),
  progress: (id: number, completed: boolean, elapsedSeconds: number) => apiRequest(`lessons/${id}/progress`, { method: "POST", body: JSON.stringify({ completed, elapsedSeconds }), keepalive: true }),
  exercises: (signal?: AbortSignal) => apiRequest<ExerciseRecord[]>("exercises", { signal }),
  studentAnalytics: async (id?: number) => {
    if (id !== undefined && (!Number.isSafeInteger(id) || id <= 0)) throw new Error("Hãy chọn học sinh để xem chi tiết.");
    return apiRequest<StudentAnalytics>(id !== undefined ? `students/${id}/statistics` : "analytics");
  },
  managementAnalytics: (signal?: AbortSignal) => apiRequest<ManagementAnalytics>("analytics", { signal }),
};
export function initialMock<T>(value: T, empty: T): T { return useMockApi ? value : empty; }
