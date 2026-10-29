const emptySelection = { courseId: 0, chapterId: 0, lessonId: 0, examId: 0, attemptId: 0, studentId: 0, editLessonId: 0, essayQuestionId: 0, submissionId: 0, questionId: 0 };
type Selection = typeof emptySelection;
let restoring = false;
let generation = 0;
let searchQuery = "";
let searchPage = 0;
export const getSearchQuery = () => searchQuery;
export const setSearchQuery = (query: string) => { searchQuery = query.trim().slice(0, 200); searchPage = 0; };
export const getSearchPage = () => searchPage;
export const setSearchPage = (page: number) => { searchPage = Number.isSafeInteger(page) && page >= 0 && page <= 50_000 ? page : 0; };
export const selectionGeneration = () => generation;
export const selection = new Proxy({ ...emptySelection }, {
  set(target, key: keyof Selection, value: number) {
    target[key] = Number.isSafeInteger(value) && value > 0 ? value : 0;
    if (!restoring && typeof window !== "undefined" && window.location.hash.startsWith("#/")) writeRoute(currentPage(), true);
    return true;
  },
});
export function resetSelection() { generation++; searchQuery = ""; searchPage = 0; restoring = true; Object.assign(selection, emptySelection); restoring = false; }
export function currentPage() { return typeof window === "undefined" ? "" : window.location.hash.slice(2).split("?")[0]; }
export function readRoute() {
  if (typeof window === "undefined") return "";
  const [page, query] = window.location.hash.slice(2).split("?");
  restoring = true;
  const params = new URLSearchParams(query);
  searchQuery = page === "search" ? (params.get("q") || "").slice(0, 200) : "";
  setSearchPage(page === "search" ? Number(params.get("page")) : 0);
  for (const key of Object.keys(emptySelection) as (keyof Selection)[]) selection[key] = Number(params.get(key));
  restoring = false;
  return page || "";
}
export function writeRoute(page: string, replace = false) {
  if (typeof window === "undefined") return;
  const params = new URLSearchParams();
  if (page === "search" && searchQuery) params.set("q", searchQuery);
  if (page === "search" && searchPage) params.set("page", String(searchPage));
  for (const [key, value] of Object.entries(selection)) if (value) params.set(key, String(value));
  const hash = `#/${page}${params.size ? `?${params}` : ""}`;
  if (window.location.hash !== hash) window.history[replace ? "replaceState" : "pushState"](null, "", hash);
}
const student = ["dashboard", "courses", "chapter", "lesson", "lessons", "quiz", "quiz-result", "ai", "exercises", "history", "essay-practice", "essay-tutor", "essay-result", "progress", "results"];
const staff = ["lessons", "create-lesson", "questions", "generator", "exam-generator", "students", "student-detail", "progress"];
export function canAccess(page: string, role: string) {
  if (["profile", "notifications", "search"].includes(page)) return ["Học sinh", "Giáo viên", "Quản trị viên"].includes(role);
  if (role === "Học sinh") return student.includes(page);
  if (role === "Giáo viên") return ["teacher", "ai", ...staff].includes(page);
  if (role === "Quản trị viên") return ["admin", "users", "courses", "system", "ai", ...staff].includes(page);
  return false;
}
export const homePage = (role: string) => role === "Giáo viên" ? "teacher" : role === "Quản trị viên" ? "admin" : "dashboard";
