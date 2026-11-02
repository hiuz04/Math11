import type { QuizAttempt } from "./quizApiService"
import type { SubmissionContent } from "../types/lms"
export function draftKey(userId: number, attemptId: number) {
  return `math11:quiz:${userId}:${attemptId}`
}
export function draftPayload(
  attempt: QuizAttempt,
  answers: Record<string, string>,
  essays: Record<string, { content: SubmissionContent }>,
) {
  let pendingImage = false
  const essayAnswers = Object.fromEntries(
    Object.entries(essays).map(([id, { content }]) => {
      const { imageFile, image, imagePending, ...rest } = content
      const missingImage = Boolean(imageFile || image?.startsWith("blob:") || imagePending)
      if (missingImage) pendingImage = true
      return [
        id,
        {
          content: {
            ...rest,
            ...(missingImage ? {imagePending:true} : {}),
            ...(!imageFile && !image?.startsWith("blob:") ? { image } : {}),
          },
        },
      ]
    }),
  )
  return { revision: attempt.revision, answers, essayAnswers, pendingImage }
}
