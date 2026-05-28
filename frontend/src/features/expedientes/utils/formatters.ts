export function formatDateTime(value?: string | null): string {
  if (!value) return "Sin fecha";

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  return new Intl.DateTimeFormat("es-ES", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

export function humanizeEnum(value?: string | null): string {
  if (!value) return "Sin definir";

  return value
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/^./, (letter) => letter.toUpperCase());
}
