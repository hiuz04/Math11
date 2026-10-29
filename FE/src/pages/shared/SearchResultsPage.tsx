import { useEffect, useState } from "react";
import { searchService, type SearchResult } from "../../services/searchService";
import { getSearchQuery, setSearchQuery, getSearchPage, setSearchPage, writeRoute } from "../../services/routeState";
import { selection } from "../../services/lmsService";
import { useMockApi, errorMessage } from "../../services/serviceMode";
import PageHeading from "../../components/common/PageHeading";
import EmptyState from "../../components/common/EmptyState";
import Icon from "../../components/common/Icon";
import Button from "../../components/common/Button";

export default function SearchResultsPage({role,setActive}:{role:string;setActive:(page:string)=>void}) {
  const [query,setQuery]=useState(getSearchQuery), [submitted,setSubmitted]=useState(getSearchQuery), [page,setPage]=useState(getSearchPage), [revision,setRevision]=useState(0);
  const [rows,setRows]=useState<SearchResult[]>([]), [hasNext,setHasNext]=useState(false), [loading,setLoading]=useState(true), [error,setError]=useState("");
  useEffect(()=>{const controller=new AbortController();let live=true;setLoading(true);setError("");setRows([]);setHasNext(false);Promise.resolve().then(()=>{controller.signal.throwIfAborted();return searchService.search(submitted,role==="Học sinh",page,controller.signal);}).then(result=>{if(live){setRows(result.rows);setHasNext(result.hasNext);}}).catch(e=>{if(live&&!controller.signal.aborted)setError(errorMessage(e));}).finally(()=>{if(live)setLoading(false);});return()=>{live=false;controller.abort();};},[role,submitted,page,revision]);
  function submitSearch(){setSearchQuery(query);setSearchPage(0);writeRoute("search",true);setSubmitted(query.trim());setPage(0);setRevision(v=>v+1);}
  function changePage(next:number){setSearchPage(next);writeRoute("search",true);setPage(next);}
  const labels={lesson:"Bài giảng",essay:"Tự luận",quiz:"Kiểm tra",question:"Câu hỏi"};
  function open(record:SearchResult) {
    if(record.kind==="lesson") { selection.courseId=record.courseId || 0;selection.chapterId=record.chapterId || 0;if(role==="Học sinh"){selection.lessonId=useMockApi?0:record.id;setActive("lesson");}else{selection.editLessonId=record.id;setActive("create-lesson");} }
    else if(record.kind==="question"){selection.questionId=record.id;setActive("questions");}
    else if(record.kind==="essay"){selection.essayQuestionId=record.id;setActive("essay-practice");}
    else{selection.examId=useMockApi?0:record.id;selection.attemptId=0;setActive("quiz");}
  }
  return <div className="page"><PageHeading eyebrow="NỘI DUNG HỌC TẬP" title="Kết quả tìm kiếm" subtitle="Tìm bài giảng và bài tập trong phạm vi được phép truy cập."/><div className="filter-bar"><div className="search-box"><Icon name="search" size={18}/><input maxLength={200} aria-label="Từ khóa tìm kiếm" placeholder="Tìm bài học, bài tập..." value={query} onChange={e=>setQuery(e.target.value)} onKeyDown={e=>{if(e.key==="Enter")submitSearch();}}/></div><Button variant="secondary" onClick={submitSearch}>Tìm kiếm</Button></div>{error && <p className="feature-error" role="alert">{error}</p>}{loading ? <p role="status">Đang tìm nội dung…</p> : !error && (rows.length ? <section className="card data-card"><div className="table-scroll"><table><thead><tr><th>TÊN NỘI DUNG</th><th>CHƯƠNG</th><th>LOẠI</th><th>THAO TÁC</th></tr></thead><tbody>{rows.map(record=><tr key={`${record.kind}:${record.id}`}><td><strong>{record.title}</strong></td><td>{record.chapter || "—"}</td><td>{labels[record.kind]}</td><td><div className="row-actions"><button onClick={()=>open(record)}>Xem</button></div></td></tr>)}</tbody></table></div><div className="pagination"><span>{rows.length} kết quả trên trang</span><div><button aria-label="Trang trước" disabled={page===0 || loading} onClick={()=>changePage(page-1)}>‹</button><button className="active" aria-current="page">{page+1}</button><button aria-label="Trang sau" disabled={!hasNext || loading} onClick={()=>changePage(page+1)}>›</button></div></div></section> : <EmptyState title={submitted.trim() ? "Không tìm thấy kết quả" : "Nhập từ khóa tìm kiếm"} description="Tìm theo tên bài giảng, bài tập hoặc chương."/>)}</div>;
}
