export function uppercaseInput(value: string): string {
  return value.toLocaleUpperCase("es-ES");
}

export function cleanUpperText(value?: string | null): string | null {
  const trimmed = value?.trim();
  return trimmed ? uppercaseInput(trimmed) : null;
}

export function cleanLowerText(value?: string | null): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed.toLocaleLowerCase("es-ES") : null;
}
