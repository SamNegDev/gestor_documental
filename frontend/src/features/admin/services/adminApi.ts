import { apiDelete, apiGet, apiPostJson, apiPutJson } from "../../../shared/api/http";
import type { ClienteAdmin, ClienteInput, UsuarioAdmin, UsuarioCatalogs, UsuarioInput } from "../types";

export function getClientes() {
  return apiGet<ClienteAdmin[]>("/api/admin/clientes");
}

export function getCliente(id: string | number) {
  return apiGet<ClienteAdmin>(`/api/admin/clientes/${id}`);
}

export function createCliente(input: ClienteInput) {
  return apiPostJson<{ id: number }>("/api/admin/clientes", input);
}

export function updateCliente(id: string | number, input: ClienteInput) {
  return apiPutJson(`/api/admin/clientes/${id}`, input);
}

export function deleteCliente(id: number) {
  return apiDelete(`/api/admin/clientes/${id}`);
}

export function getUsuarios() {
  return apiGet<UsuarioAdmin[]>("/api/admin/usuarios");
}

export function getUsuario(id: string | number) {
  return apiGet<UsuarioAdmin>(`/api/admin/usuarios/${id}`);
}

export function getUsuarioCatalogs() {
  return apiGet<UsuarioCatalogs>("/api/admin/usuarios/catalogos");
}

export function createUsuario(input: UsuarioInput) {
  return apiPostJson<{ id: number }>("/api/admin/usuarios", input);
}

export function updateUsuario(id: string | number, input: UsuarioInput) {
  return apiPutJson(`/api/admin/usuarios/${id}`, input);
}

export function deleteUsuario(id: number) {
  return apiDelete(`/api/admin/usuarios/${id}`);
}
