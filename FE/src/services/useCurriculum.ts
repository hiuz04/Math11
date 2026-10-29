import { curriculum as demoCurriculum } from "../constants/lms";
import { apiRequest } from "./api";
import { useResource } from "./useResource";
import { useMockApi } from "./serviceMode";
export function useCurriculum() {
  return useResource(async () => {
    const [chapters, topics] = await Promise.all([
      apiRequest<{id:number;title:string}[]>("chapters"),
      apiRequest<{id:number;chapterId:number;title:string}[]>("topics"),
    ]);
    return Object.fromEntries(chapters.map(c=>[c.title,topics.filter(t=>t.chapterId===c.id).map(t=>t.title)]).filter(([,items])=>(items as string[]).length>0)) as Record<string,string[]>;
  }, useMockApi ? demoCurriculum : {} as Record<string,string[]>);
}
