import { apiRequest } from "./api";
import { postApi } from "./serviceMode";
export interface NotificationRecord { id:number;content:string;isRead:boolean;createdAt:string }
export const notificationService = {
  list: (signal?:AbortSignal) => apiRequest<NotificationRecord[]>("notifications",{signal}),
  read: (id:number) => postApi(`notifications/${id}/read`,{}),
};
