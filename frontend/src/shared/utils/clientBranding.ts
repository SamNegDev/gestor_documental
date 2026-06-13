export function clientInitials(name?: string | null) {
  const parts = (name || "").trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "CL";
  return parts.slice(0, 2).map((part) => part[0]).join("").toUpperCase();
}
