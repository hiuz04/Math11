import { managementTitles } from "../../constants/mockData";
import Icon from "../../components/common/Icon";
import Button from "../../components/common/Button";
import Badge from "../../components/common/Badge";
import Generator from "../teacher/QuestionGeneratorPage";
import PageHeading from "../../components/common/PageHeading";
import ApiDataManagementPage from "./ApiDataManagementPage";
import { useMockApi } from "../../services/serviceMode";

export default function DataManagement({ page, role, setActive, notify, confirm }: { page: string; role: string; setActive: (p:string)=>void; notify:(m:string)=>void; confirm:(title:string,desc:string,action:()=>void)=>void }) {
  if (!useMockApi) return <ApiDataManagementPage page={page} role={role} setActive={setActive} notify={notify} confirm={confirm}/>;
  const titles: Record<string, [string,string]> = managementTitles;
  const t = titles[page] || ["Nội dung học tập","Quản lý tài nguyên trên hệ thống."];
  if (page === "generator") return <Generator notify={notify} setActive={setActive}/>;
  return <div className="page"><PageHeading eyebrow={role === "Quản trị viên" ? "QUẢN TRỊ" : "QUẢN LÝ NỘI DUNG"} title={t[0]} subtitle={t[1]} action={<Button icon="plus" onClick={() => page === "lessons" ? setActive("create-lesson") : notify("Đã mở biểu mẫu thêm mới.")}>Thêm mới</Button>}/>
    <div className="filter-bar"><div className="search-box"><Icon name="search" size={18}/><input placeholder={`Tìm kiếm ${t[0].toLowerCase()}...`}/></div><select><option>Tất cả trạng thái</option></select><select><option>Tất cả chương</option></select><Button variant="secondary">Bộ lọc</Button></div>
    <section className="card data-card"><div className="table-scroll"><table><thead><tr><th>{page === "users" || page === "students" ? "HỌ VÀ TÊN" : "TÊN NỘI DUNG"}</th><th>{page === "users" ? "VAI TRÒ" : "CHƯƠNG"}</th><th>TRẠNG THÁI</th><th>NGÀY TẠO</th><th>THAO TÁC</th></tr></thead><tbody>{[
      ["Hàm số lượng giác và ứng dụng","Chương 1","Đã xuất bản","20/06/2025"],
      ["Phương trình lượng giác cơ bản","Chương 1","Đã xuất bản","18/06/2025"],
      ["Cấp số cộng và bài toán thực tế","Chương 2","Bản nháp","16/06/2025"],
      ["Giới hạn hữu hạn của dãy số","Chương 3","Đã xuất bản","12/06/2025"],
      ["Quan hệ song song trong không gian","Chương 5","Đang duyệt","08/06/2025"],
    ].map((r,i)=><tr key={r[0]}><td><div className="entity-cell"><span className="entity-avatar">{page === "users" || page === "students" ? ["NA","HN","TL","MP","QH"][i] : <Icon name="book" size={18}/>}</span><div><strong>{page === "users" || page === "students" ? ["Nguyễn Minh Anh","Hoàng Gia Nam","Trần Khánh Linh","Mai Hương Phạm","Vũ Quang Huy"][i] : r[0]}</strong><span>{page === "users" || page === "students" ? `student${i+1}@mathio.vn` : `${8+i*2} bài học`}</span></div></div></td><td>{page === "users" ? <Badge tone={i===4 ? "purple" : "blue"}>{i===4 ? "Giáo viên" : "Học sinh"}</Badge> : r[1]}</td><td><Badge tone={r[2] === "Đã xuất bản" ? "green" : r[2] === "Bản nháp" ? "orange" : "blue"}>{page === "users" || page === "students" ? "Đang hoạt động" : r[2]}</Badge></td><td>{r[3]}</td><td><div className="row-actions"><button onClick={()=>page==="students"?setActive("student-detail"):notify("Đang mở nội dung...")}>Xem</button><button onClick={()=>notify("Đã lưu thay đổi.")}>Sửa</button><button className="danger-link" onClick={()=>confirm("Xóa mục này?", "Bạn có chắc muốn xóa? Hành động này không thể hoàn tác.", ()=>notify("Đã xóa khỏi danh sách."))}>Xóa</button></div></td></tr>)}</tbody></table></div><div className="pagination"><span>Hiển thị 1–5 trong 56 kết quả</span><div><button>‹</button><button className="active">1</button><button>2</button><button>3</button><button>›</button></div></div></section>
  </div>;
}
