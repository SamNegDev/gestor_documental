import type { ClienteResumen } from "../expedientes/types/expedienteDetail.types";
import type { DocumentoExpediente } from "../expedientes/types/expedienteDetail.types";

export interface ClienteAdmin {
  id: number;
  nif: string;
  nombre: string;
  email: string;
  direccion?: string | null;
  telefono?: string | null;
  preferenciaCanal?: "EMAIL" | "WHATSAPP" | "AMBOS" | "SIN_AVISOS" | null;
  logoPrincipalUrl?: string | null;
  logoCompactoUrl?: string | null;
  documentos?: DocumentoExpediente[];
}

export interface ClienteInput {
  nif: string;
  nombre: string;
  email: string;
  direccion?: string | null;
  telefono?: string | null;
  preferenciaCanal?: "EMAIL" | "WHATSAPP" | "AMBOS" | "SIN_AVISOS" | null;
}

export interface ResumenDiarioResponse {
  clientesEnviados: number;
  cambiosIncluidos: number;
  avisos: string[];
}

export interface UsuarioAdmin {
  id: number;
  nombre: string;
  apellidos: string;
  nombreCompleto: string;
  email: string;
  rol: string;
  activo: boolean;
  cliente?: ClienteResumen | null;
}

export interface UsuarioInput {
  nombre: string;
  apellidos: string;
  email: string;
  password?: string | null;
  rolUsuario: string;
  activo: boolean;
  clienteId?: number | null;
}

export interface UsuarioCatalogs {
  roles: string[];
  clientes: ClienteResumen[];
}
