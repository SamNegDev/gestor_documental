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
  avisoIncidenciasActivo: boolean;
  horaAvisoIncidencias: string;
  avisoFinalizadosActivo: boolean;
  horaAvisoFinalizados: string;
  logoPrincipalUrl?: string | null;
  logoCompactoUrl?: string | null;
  documentos?: DocumentoExpediente[];
  administradores?: AdministradorCliente[];
}

export interface AdministradorCliente { id: number; dni: string; nombre: string; telefono?: string | null; direccion?: string | null; }
export type AdministradorClienteInput = Omit<AdministradorCliente, "id">;

export interface ClienteInput {
  nif: string;
  nombre: string;
  email: string;
  direccion?: string | null;
  telefono?: string | null;
  preferenciaCanal?: "EMAIL" | "WHATSAPP" | "AMBOS" | "SIN_AVISOS" | null;
  avisoIncidenciasActivo: boolean;
  horaAvisoIncidencias: string;
  avisoFinalizadosActivo: boolean;
  horaAvisoFinalizados: string;
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
