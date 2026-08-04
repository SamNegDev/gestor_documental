import type { CategoriaHistorial } from "../types/expedienteDetail.types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

type ExportOptions = {
  categoria?: CategoriaHistorial;
  desde?: string;
  hasta?: string;
  clientView?: boolean;
};

export async function downloadExpedienteHistoryCsv(
  expedienteId: string | number,
  options: ExportOptions,
): Promise<void> {
  const params = new URLSearchParams();
  if (options.categoria) params.set("categoria", options.categoria);
  if (options.desde) params.set("desde", options.desde);
  if (options.hasta) params.set("hasta", options.hasta);
  const prefix = options.clientView ? "/api/cliente" : "/api";
  const query = params.size ? `?${params.toString()}` : "";
  const response = await fetch(
    `${API_BASE_URL}${prefix}/expedientes/${expedienteId}/historial/exportar${query}`,
    { credentials: "include", headers: { Accept: "text/csv" } },
  );
  if (!response.ok) {
    throw new Error("No se pudo exportar el historial");
  }

  const disposition = response.headers.get("content-disposition") ?? "";
  const filename = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition)?.[1]
    ?? `historial-expediente-${expedienteId}.csv`;
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement("a");
  link.href = url;
  link.download = decodeURIComponent(filename);
  link.click();
  URL.revokeObjectURL(url);
}
