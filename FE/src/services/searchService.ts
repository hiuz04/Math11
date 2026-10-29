import { questionService } from "./questionService";
import { useMockApi } from "./serviceMode";
import { lessonService } from "./lessonService";
import { exerciseService } from "./exerciseService";
import { apiRequest } from "./api";
export interface SearchResult { id: number; title: string; chapter: string; kind: "lesson" | "essay" | "quiz" | "question"; courseId?: number; chapterId?: number }
export const searchText = (value: string) => value.toLocaleLowerCase("vi").normalize("NFD").replace(/\p{Diacritic}/gu, "").replace(/đ/g, "d");
export const searchService = {
  async search(query: string, student: boolean, page: number, signal?: AbortSignal) {
    if (!query.trim()) return { rows: [] as SearchResult[], hasNext: false };
    const rows = useMockApi
      ? (await this.list(student)).filter(r=>searchText(r.title+" "+r.chapter).includes(searchText(query))).slice(page*20,page*20+21)
      : await apiRequest<SearchResult[]>(`search?${new URLSearchParams({q:query,offset:String(page*20),limit:"21"})}`, { signal });
    return { rows: rows.slice(0,20), hasNext: rows.length > 20 };
  },
  async list(student: boolean): Promise<SearchResult[]> {
    if (!useMockApi) throw new Error("Danh sách demo không khả dụng trong chế độ API thật.");
    const lessons: SearchResult[] = lessonService.getLessons().map((l,index)=>({id:index+1,title:l[0],chapter:"",kind:"lesson"}));
    if (!student) return [...lessons,...(await questionService.list()).map(q=>({id:q.id,title:q.title,chapter:q.chapter,kind:"question" as const}))];
    return [...lessons,...exerciseService.getExercises().map((e,index)=>({id:e[2]==="Tự luận"?(e[0].includes("cấp số nhân")?102:101):index+1,title:e[0],chapter:e[1],kind:e[2]==="Tự luận"?"essay" as const:"quiz" as const}))];
  },
};
