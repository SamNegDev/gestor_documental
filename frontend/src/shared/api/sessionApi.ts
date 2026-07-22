import { apiGet, apiPutJsonResponse } from "./http";

export interface SessionClient {
  id: number;
  nombre: string;
  nif?: string | null;
  logoPrincipalUrl?: string | null;
  logoCompactoUrl?: string | null;
}

export interface SessionUser {
  id: number;
  nombreCompleto: string;
  email?: string | null;
  rol?: "ADMIN" | "CLIENTE" | string | null;
  cliente?: SessionClient | null;
  clientes: SessionClient[];
}

export function getSessionUser(): Promise<SessionUser> {
  return apiGet<SessionUser>("/api/session");
}

export function selectActiveClient(clienteId: number | null): Promise<SessionUser> {
  return apiPutJsonResponse<SessionUser>("/api/session/cliente-activo", { clienteId });
}
