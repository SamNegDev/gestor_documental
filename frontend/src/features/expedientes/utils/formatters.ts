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

  const acronyms = new Set(["DNI", "NIF", "NIE", "CIF", "DGT", "OCR"]);

  return value
    .split("_")
    .filter(Boolean)
    .map((part) => {
      const upper = part.toUpperCase();
      if (acronyms.has(upper) || /\d/.test(part)) return upper;
      return part.toLowerCase().replace(/^./, (letter) => letter.toUpperCase());
    })
    .join(" ");
}

export function formatDocumentType(value?: string | null): string {
  return value ? value.replaceAll("_", " ").toUpperCase() : "SIN DEFINIR";
}
