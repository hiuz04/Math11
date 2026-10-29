import { useEffect, useState, type ReactNode } from "react";
import Sidebar from "./components/layout/Sidebar";
import Header from "./components/layout/Topbar";
import Toast from "./components/common/Toast";
import Modal from "./components/common/Modal";
import Login from "./pages/auth/LoginPage";
import Dashboard from "./pages/student/StudentDashboard";
import Courses from "./pages/student/CoursesPage";
import ChapterDetail from "./pages/student/CourseDetailPage";
import QuizResult from "./pages/student/QuizResultPage";
import NotificationsPage from "./pages/shared/NotificationsPage";
import ProfilePage from "./pages/shared/ProfilePage";
import StudentDetail from "./pages/teacher/StudentDetailPage";
import UserManagement from "./pages/admin/UserManagementPage";
import DataManagement from "./pages/management/DataManagementPage";
import { authService } from "./services/authService";
import { useMockApi, errorMessage } from "./services/serviceMode";
import { selection } from "./services/lmsService";
import EssayPracticePage from "./pages/student/EssayPracticePage";
import EssayTutorPage from "./pages/student/EssayTutorPage";
import EssayResultPage from "./pages/student/EssayResultPage";
import LearningHistoryPage from "./pages/student/LearningHistoryPage";
import ExamGeneratorPage from "./pages/teacher/ExamGeneratorPage";
import QuestionBankPage from "./pages/teacher/QuestionBankPage";
import SystemConfigurationPage from "./pages/admin/SystemConfigurationPage";
import ExercisesPage from "./pages/student/ExercisesPage";
import StudentProgressPage from "./pages/student/ProgressPage";
import LessonEditorPage from "./pages/teacher/CreateLessonPage";
import QuestionGeneratorPage from "./pages/teacher/QuestionGeneratorPage";
import StudentQuizPage from "./pages/student/QuizPage";
import ManagementDashboardPage from "./pages/management/ManagementDashboard";
import StudentLessonPage from "./pages/student/LessonPage";
import StudentAITutorPage from "./pages/student/AITutorPage";
import { canAccess, homePage, readRoute, writeRoute, resetSelection } from "./services/routeState";
import SearchResultsPage from "./pages/shared/SearchResultsPage";
import { useResource } from "./services/useResource";
import { notificationService, type NotificationRecord } from "./services/notificationService";

export default function App() {
  const [loggedIn, setLoggedIn] = useState(false);
  const [sessionPending,setSessionPending] = useState(!useMockApi);
  const [role, setRole] = useState("Học sinh");
  const [active, updateActive] = useState(readRoute);
  const [routeRevision, setRouteRevision] = useState(0);
  const {data:notifications,setData:setNotifications,error:notificationError,loading:notificationsPending}=useResource(signal=>loggedIn?notificationService.list(signal):Promise.resolve([]),[] as NotificationRecord[],[loggedIn,active]);
  const setActive = (page: string) => { writeRoute(page); updateActive(page); setRouteRevision(v => v + 1); };
  const essayQuestionId = selection.essayQuestionId || (useMockApi ? 101 : 0);
  const submissionId = selection.submissionId;
  const [quizCategory, setQuizCategory] = useState<"Luyện tập" | "Kiểm tra">("Kiểm tra");
  const openQuiz = () => { setQuizCategory("Luyện tập"); setActive("quiz"); };
  const openEssay = (id: number) => { selection.essayQuestionId = id; setActive("essay-practice"); };
  const openSubmission = (id: number, tutor = false) => { selection.submissionId = id; setActive(tutor ? "essay-tutor" : "essay-result"); };
  const [mobileOpen, setMobileOpen] = useState(false);
  const [toast, setToast] = useState<{type:"success"|"error"|"warning";message:string}|null>(null);
  const [confirmState, setConfirmState] = useState<{title:string;description:string;action:()=>void;confirmText?:string;danger?:boolean}|null>(null);
  const notify = (message:string, type:"success"|"error"|"warning"="success") => setToast({message,type});
  const confirm = (title:string,description:string,action:()=>void,confirmText="Xác nhận",danger=false) => setConfirmState({title,description,action,confirmText,danger});
  const login = (nextRole: string) => {
    setNotifications([]);
    resetSelection();
    setRole(nextRole);
    setLoggedIn(true);
    setActive(homePage(nextRole));
    notify(`Đăng nhập thành công với vai trò ${nextRole}.`);
  };
  useEffect(() => {
    if (useMockApi) return;
    let live = true;
    authService.restore().then(next => { if (live && next) { setRole(next); setLoggedIn(true); const page = readRoute() || homePage(next); writeRoute(page, true); updateActive(page); } }).catch(e => { if (live && !(e && (e.status === 401 || e.name === "AbortError"))) notify(errorMessage(e), "error"); }).finally(()=>{if(live)setSessionPending(false);});
    const expired = () => { authService.clear(); resetSelection(); setSessionPending(false); setLoggedIn(false); writeRoute("", true); updateActive(""); };
    window.addEventListener("mathio:session-expired", expired);
    return () => { live = false; window.removeEventListener("mathio:session-expired", expired); };
  }, []);
  useEffect(() => {
    const changed = () => { updateActive(readRoute() || homePage(role)); setRouteRevision(v => v + 1); };
    window.addEventListener("popstate", changed); window.addEventListener("hashchange", changed);
    return () => { window.removeEventListener("popstate", changed); window.removeEventListener("hashchange", changed); };
  }, [role]);
  useEffect(() => { const failed = (event: Event) => notify((event as CustomEvent<string>).detail, "error"); window.addEventListener("mathio:background-error", failed); return () => window.removeEventListener("mathio:background-error", failed); }, []);
  const askLogout = () => confirm("Đăng xuất?", "Bạn có chắc muốn đăng xuất khỏi Mathio?", () => { void authService.logout().then(() => { setLoggedIn(false); resetSelection(); setActive(""); setConfirmState(null); }).catch(e => notify(errorMessage(e), "error")); }, "Đăng xuất", true);
  if (!loggedIn) return <><Login onLogin={login} sessionPending={sessionPending}/><Toast toast={toast} clear={() => setToast(null)}/></>;
  let content: ReactNode;
  if (!canAccess(active, role)) content = <div className="page"><p className="feature-error" role="alert">Bạn không có quyền truy cập trang này.</p><button onClick={() => setActive(homePage(role))}>Về trang chủ</button></div>;
  else if (active === "dashboard") content = <Dashboard setActive={setActive}/>;
  else if (active === "courses" && role === "Học sinh") content = <Courses setActive={setActive}/>;
  else if (active === "chapter") content = <ChapterDetail setActive={setActive}/>;
  else if (active === "lesson") content = <StudentLessonPage setActive={setActive} notify={m=>notify(m)}/>;
  else if (active === "quiz") content = <StudentQuizPage setActive={setActive} notify={notify} category={quizCategory}/>;
  else if (active === "quiz-result") content = <QuizResult setActive={setActive}/>;
  else if (active === "ai") content = <StudentAITutorPage/>;
  else if (active === "exercises") content = <ExercisesPage setActive={setActive} openEssay={openEssay} openQuiz={openQuiz}/>;
  else if (active === "history" && role === "Học sinh") content = <LearningHistoryPage onOpen={openSubmission} onQuiz={id=>{selection.attemptId=id;setActive("quiz-result");}}/>;
  else if (active === "essay-practice" && role === "Học sinh") content = <EssayPracticePage key={essayQuestionId} questionId={essayQuestionId} setActive={setActive} onSubmission={openSubmission}/>;
  else if (active === "essay-tutor" && role === "Học sinh") content = <EssayTutorPage key={submissionId} submissionId={submissionId} setActive={setActive}/>;
  else if (active === "essay-result" && role === "Học sinh") content = <EssayResultPage key={submissionId} submissionId={submissionId} setActive={setActive}/>;
  else if (active === "exam-generator" && role !== "Học sinh") content = <ExamGeneratorPage setActive={setActive} notify={m => notify(m)}/>;
  else if (active === "generator" && role !== "Học sinh") content = <QuestionGeneratorPage setActive={setActive} notify={m => notify(m)}/>;
  else if (active === "questions" && role !== "Học sinh") content = <QuestionBankPage setActive={setActive} notify={m => notify(m)} confirm={confirm}/>;
  else if (active === "system" && role === "Quản trị viên") content = <SystemConfigurationPage notify={m => notify(m)}/>;
  else if ((active === "progress" || active === "results") && role === "Học sinh") content = <StudentProgressPage setActive={setActive}/>;
  else if (active === "progress" && role !== "Học sinh") content = <ManagementDashboardPage role={role} setActive={setActive}/>;
  else if (active === "lessons" && role === "Học sinh") content = <ChapterDetail setActive={setActive}/>;
  else if (active === "teacher" || active === "admin") content = <ManagementDashboardPage role={role} setActive={setActive}/>;
  else if (active === "notifications") content = <NotificationsPage records={notifications} error={notificationError} loading={notificationsPending} onRead={id=>setNotifications(rows=>rows.map(row=>row.id===id?{...row,isRead:true}:row))}/>;
  else if (active === "profile") content = <ProfilePage role={role} notify={m=>notify(m)}/>;
  else if (active === "search") content = <SearchResultsPage role={role} setActive={setActive}/>;
  else if (active === "create-lesson") content = <LessonEditorPage setActive={setActive} notify={m=>notify(m)}/>;
  else if (active === "student-detail") content = <StudentDetail setActive={setActive}/>;
  else if (active === "users" && role === "Quản trị viên") content = <UserManagement notify={m=>notify(m)} confirm={confirm}/>;
  else if (["courses", "lessons", "students"].includes(active)) content = <DataManagement key={active} page={active} role={role} setActive={setActive} notify={m=>notify(m)} confirm={confirm}/>;
  else content = null;
  return <div className="app-shell">
    <Sidebar notifications={notifications} active={active} setActive={setActive} role={role} mobileOpen={mobileOpen} close={() => setMobileOpen(false)} onLogout={askLogout}/>
    {mobileOpen && <div className="overlay" onClick={() => setMobileOpen(false)}/>}
    <div className="app-main"><Header notifications={notifications} notificationError={notificationError} role={role} openMenu={() => setMobileOpen(true)} setActive={setActive} onLogout={askLogout}/><main key={`${active}:${routeRevision}`}>{content}</main></div>
    <Toast toast={toast} clear={() => setToast(null)}/>
    {confirmState && <Modal title={confirmState.title} description={confirmState.description} confirmText={confirmState.confirmText || "Xác nhận"} danger={confirmState.danger} onClose={()=>setConfirmState(null)} onConfirm={()=>{confirmState.action();setConfirmState(null);}}/>}
  </div>;
}
