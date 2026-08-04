import type { ClienteResumen } from "../expedientes/types/expedienteDetail.types";
import type { DocumentoExpediente } from "../expedientes/types/expedienteDetail.types";
import type { AddressValue } from "../../shared/ui/AddressFields";

export interface ClienteAdmin extends AddressValue {
  id: number;
  nif: string;
  nombre: string;
  email: string;
  emailNotificaciones?: string | null;
  emailsCopiaNotificaciones: string[];
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

export interface AdministradorCliente extends AddressValue { id: number; dni: string; nombre: string; telefono?: string | null; }
export type AdministradorClienteInput = Omit<AdministradorCliente, "id">;

export interface ClienteInput extends AddressValue {
  nif: string;
  nombre: string;
  emailNotificaciones?: string | null;
  emailsCopiaNotificaciones: string[];
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
  clientes: ClienteResumen[];
  apellidos: string;
  nombreCompleto: string;
  email: string;
  rol: string;
  activo: boolean;
  cliente?: ClienteResumen | null;
}

export interface UsuarioInput {
  nombre: string;
  clienteIds: number[];
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
