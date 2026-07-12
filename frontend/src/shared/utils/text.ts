import type { ChangeEvent } from "react";

export function uppercaseInput(value: string): string {
  return value.toLocaleUpperCase("es-ES");
}

export function uppercaseInputPreservingCursor<T extends HTMLInputElement | HTMLTextAreaElement>(
  event: ChangeEvent<T>,
  applyValue: (value: string) => void,
): void {
  const input = event.currentTarget;
  const rawValue = input.value;
  const selectionStart = input.selectionStart;
  const selectionEnd = input.selectionEnd;
  const nextValue = uppercaseInput(rawValue);
  const nextSelectionStart = selectionStart === null ? null : uppercaseInput(rawValue.slice(0, selectionStart)).length;
  const nextSelectionEnd = selectionEnd === null ? null : uppercaseInput(rawValue.slice(0, selectionEnd)).length;

  applyValue(nextValue);

  if (nextSelectionStart === null || nextSelectionEnd === null) return;
  window.requestAnimationFrame(() => {
    if (document.activeElement !== input) return;
    input.setSelectionRange(nextSelectionStart, nextSelectionEnd);
  });
}

export function cleanUpperText(value?: string | null): string | null {
  const trimmed = value?.trim();
  return trimmed ? uppercaseInput(trimmed) : null;
}

export function cleanLowerText(value?: string | null): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed.toLocaleLowerCase("es-ES") : null;
}
