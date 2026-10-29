export interface ToastMessage { type: "success" | "error" | "warning"; message: string }
export interface Confirmation { title: string; description: string; action: () => void; confirmText?: string; danger?: boolean }
