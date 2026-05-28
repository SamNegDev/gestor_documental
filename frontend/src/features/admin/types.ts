import type { ClienteResumen } from "../expedientes/types/expedienteDetail.types";

export interface ClienteAdmin {
  id: number;
  nif: string;
  nombre: string;
  email: string;
  direccion?: string | null;
  telefono?: string | null;
}

export interface ClienteInput {
  nif: string;
  nombre: string;
  email: string;
  direccion?: string | null;
  telefono?: string | null;
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
