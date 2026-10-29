import { useEffect } from "react";
import { useResource } from "../../services/useResource";
import { lmsService } from "../../services/lmsService";
import { useMockApi } from "../../services/serviceMode";

/** Bind the existing chapter dropdown to an explicit authorized course, without adding a form row. */
export default function CourseChapterSelect<T extends string | number>({ courseId, chapter, chapters, onChange, disabled = false }: {
  courseId: number; chapter: T; chapters: { value: T; title: string }[];
  onChange: (courseId: number, chapter: T) => void; disabled?: boolean;
}) {
  const { data: courses, loading, error } = useResource(lmsService.courses, []);
  const index = chapters.findIndex(c => c.value === chapter);
  useEffect(() => {
    if (useMockApi || loading || !courses.length || !chapters.length) return;
    if (!courses.some(c => c.id === courseId)) {
      if (courses.length === 1) onChange(courses[0].id, chapters[Math.max(0, index)].value);
      else if (courseId) onChange(0, chapter);
    } else if (index < 0) onChange(courseId, chapters[0].value);
  }, [courses, loading, chapters, courseId, index]);
  return <><select disabled={disabled || loading} value={useMockApi ? String(chapter) : courses.some(c => c.id === courseId) && index >= 0 ? `${courseId}:${index}` : ""} onChange={event => {
    if (useMockApi) { const chosen = chapters.find(c => String(c.value) === event.target.value); if (chosen) onChange(courseId, chosen.value); return; }
    const [course, position] = event.target.value.split(":").map(Number);
    if (courses.some(c => c.id === course) && chapters[position]) onChange(course, chapters[position].value);
  }}>{useMockApi ? chapters.map(c => <option key={String(c.value)} value={String(c.value)}>{c.title}</option>) : <><option value="">Chọn chương và khóa học</option>{courses.map(course => <optgroup key={course.id} label={course.title}>{chapters.map((c, position) => <option key={String(c.value)} value={`${course.id}:${position}`}>{c.title}</option>)}</optgroup>)}</>}</select>{error && <p className="feature-error" role="alert">{error}</p>}</>;
}
