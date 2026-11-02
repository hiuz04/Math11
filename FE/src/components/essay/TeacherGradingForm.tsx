import { useRef, useState } from "react"
import type { EssaySubmission } from "../../types/lms"
import { quizApiService, type QuizAttempt } from "../../services/quizApiService"
import { essayService } from "../../services/essayService"
import { errorMessage } from "../../services/serviceMode"
import Button from "../common/Button"

type Detail = EssaySubmission | QuizAttempt
export default function TeacherGradingForm({
  detail,
  onSaved,
}: {
  detail: Detail
  onSaved: (detail: Detail) => void
}) {
  const standalone = "questionId" in detail
  const questions = standalone
    ? [detail.question]
    : detail.questions.filter((q) => q.type === "ESSAY")
  const [grades, setGrades] = useState<Record<number, {
    score: string
    feedback: string
  }>>(() =>
    Object.fromEntries(
      questions.map((q) => {
        const saved = standalone
          ? detail.finalResult
          : detail.essayAnswers?.[q.id]
        return [
          q.id,
          {
            score: saved?.score == null ? "" : String(saved.score),
            feedback: saved?.feedback || "",
          },
        ]
      }),
    ),
  )
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [success, setSuccess] = useState("")
  const lock = useRef(false)
  const allowed = standalone
    ? !detail.finalResult?.gradedBy
    : detail.status === "PENDING_GRADING"
  async function save() {
    if (lock.current || !allowed) return
    const payload = questions.map((q) => ({
      questionId: q.id,
      score: Number(grades[q.id].score),
      feedback: grades[q.id].feedback.trim(),
    }))
    if (
      questions.some((q) => !grades[q.id].score.trim()) ||
      payload.some(
        (g) =>
          !Number.isFinite(g.score) ||
          g.score < 0 ||
          g.score > 10 ||
          !g.feedback,
      )
    ) {
      setError("Nhập điểm từ 0 đến 10 và nhận xét cho từng câu.")
      return
    }
    if (!window.confirm("Xác nhận lưu kết quả chấm bài?")) return
    lock.current = true
    setBusy(true)
    setError("")
    setSuccess("")
    try {
      const next = standalone
        ? await essayService.grade(
            detail.id,
            payload[0].score,
            payload[0].feedback,
          )
        : await quizApiService.grade(detail.id, payload)
      onSaved(next)
      setSuccess("Đã lưu kết quả chấm bài.")
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      lock.current = false
      setBusy(false)
    }
  }
  if (!questions.length) return null
  return (
    <section className="card lesson-editor">
      <h2>Chấm bài tự luận</h2>
      {!allowed && (
        <p className="feature-note">Chỉ chấm bài đã nộp và đang chờ chấm.</p>
      )}
      {questions.map((q) => (
        <div key={q.id}>
          <h3>{q.title}</h3>
          <div className="form-row">
            <label>
              Điểm (tối đa 10)
              <input
                aria-label={`Điểm: ${q.title}`}
                type="number"
                min="0"
                max="10"
                step="0.01"
                disabled={busy || !allowed}
                value={grades[q.id].score}
                onChange={(e) =>
                  setGrades((old) => ({
                    ...old,
                    [q.id]: { ...old[q.id], score: e.target.value },
                  }))
                }
              />
            </label>
            <label>
              Nhận xét
              <textarea
                disabled={busy || !allowed}
                maxLength={10000}
                value={grades[q.id].feedback}
                onChange={(e) =>
                  setGrades((old) => ({
                    ...old,
                    [q.id]: { ...old[q.id], feedback: e.target.value },
                  }))
                }
              />
            </label>
          </div>
        </div>
      ))}
      {error && (
        <p className="feature-error" role="alert">
          {error}
        </p>
      )}
      {success && <p role="status">{success}</p>}
      {allowed && (
        <Button disabled={busy} onClick={() => void save()}>
          {busy ? "Đang lưu…" : "Lưu kết quả chấm"}
        </Button>
      )}
    </section>
  )
}
