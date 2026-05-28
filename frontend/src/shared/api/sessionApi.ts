import { apiGet } from "./http";

export interface SessionUser {
  id: number;
  nombreCompleto: string;
  email?: string | null;
  rol?: "ADMIN" | "CLIENTE" | string | null;
}

export function getSessionUser(): Promise<SessionUser> {
  return apiGet<SessionUser>("/api/session");
}
