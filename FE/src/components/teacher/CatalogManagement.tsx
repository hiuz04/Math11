import { useEffect, useRef, useState } from "react"
import { apiRequest, apiOperation } from "../../services/api"
import { errorMessage } from "../../services/serviceMode"
import Button from "../common/Button"
type Chapter = { id: number; title: string; position: number }
type Topic = { id: number; title: string; chapterId: number; position?: number }
export default function CatalogManagement() {
  const [chapters, setChapters] = useState<Chapter[]>([]),
    [topics, setTopics] = useState<Topic[]>([])
  const [kind, setKind] = useState("chapters"),
    [id, setId] = useState(0),
    [title, setTitle] = useState(""),
    [position, setPosition] = useState(0),
    [chapterId, setChapterId] = useState(0)
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [success, setSuccess] = useState("")
  const lock = useRef(false)
  async function refresh() {
    const [c, t] = await Promise.all([
      apiRequest<Chapter[]>("chapters"),
      apiRequest<Topic[]>("topics"),
    ])
    setChapters(c)
    setTopics(t)
  }
  useEffect(() => {
    refresh().catch((e) => setError(errorMessage(e)))
  }, [])
  async function save(remove = false) {
    if (
      lock.current ||
      !window.confirm(remove ? "Xóa danh mục đã chọn?" : "Lưu danh mục?")
    )
      return
    lock.current = true
    setBusy(true)
    setError("")
    setSuccess("")
    try {
      const body = JSON.stringify(
        kind === "chapters"
          ? { title, position }
          : { title, chapterId, position },
      )
      await apiOperation(
        kind + (id ? "/" + id : ""),
        remove ? "delete" : body,
        { method: remove ? "DELETE" : "POST", ...(remove ? {} : { body }) },
      )
      await refresh()
      setId(0)
      setTitle("")
      setSuccess("Đã lưu danh mục.")
      window.dispatchEvent(new Event("math11:catalog-updated"))
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
      lock.current = false
    }
  }
  return (
    <section className="card lesson-editor feature-section">
      <h2>Quản lý chương và chủ đề</h2>
      {error && (
        <p role="alert" className="feature-error">
          {error}
        </p>
      )}
      {success && <p role="status">{success}</p>}
      <div className="form-row">
        <label>
          Danh mục
          <select
            disabled={busy}
            aria-label="Danh mục"
            value={kind}
            onChange={(e) => {
              setKind(e.target.value)
              setId(0)
              setTitle("")
            }}
          >
            <option value="chapters">Chương</option>
            <option value="topics">Chủ đề</option>
          </select>
        </label>
        <label>
          Mục cần sửa
          <select
            disabled={busy}
            aria-label="Mục cần sửa"
            value={id}
            onChange={(e) => {
              const id = Number(e.target.value)
              setId(id)
              if (kind === "chapters") {
                const c = chapters.find((c) => c.id === id)
                setTitle(c?.title || "")
                setPosition(c?.position || 0)
              } else {
                const t = topics.find((t) => t.id === id)
                setTitle(t?.title || "")
                setChapterId(t?.chapterId || 0)
                setPosition(t?.position || 0)
              }
            }}
          >
            <option value="0">Tạo mới</option>
            {(kind === "chapters" ? chapters : topics).map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="form-row">
        <label>
          Tên
          <input
            disabled={busy}
            maxLength={200}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </label>
        {kind === "chapters" ? (
          <label>
            Thứ tự
            <input
              disabled={busy}
              type="number"
              min="0"
              step="1"
              value={position}
              onChange={(e) => setPosition(Number(e.target.value))}
            />
          </label>
        ) : (
          <label>
            Chương
            <select
              disabled={busy}
              aria-label="Chương"
            value={chapterId}
              onChange={(e) => setChapterId(Number(e.target.value))}
            >
              <option value="0">Chọn chương</option>
              {chapters.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.title}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>
      {kind === "topics" && (
        <label>
          Thứ tự
          <input
            type="number"
            min="0"
            step="1"
            disabled={busy}
            value={position}
            onChange={(e) => setPosition(Number(e.target.value))}
          />
        </label>
      )}
      <div className="feature-actions">
        <Button
          disabled={busy || !title.trim() || (kind === "topics" && !chapterId)}
          onClick={() => void save()}
        >
          Lưu danh mục
        </Button>
        <Button
          variant="secondary"
          disabled={busy || !id}
          onClick={() => void save(true)}
        >
          Xóa danh mục
        </Button>
      </div>
    </section>
  )
}
