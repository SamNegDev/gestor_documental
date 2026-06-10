import { useEffect, useRef, useState } from "react";
import { Search, UserRoundCheck } from "lucide-react";
import { searchInteresados } from "../services/expedienteDetailApi";
import type { InteresadoSearchResult } from "../types/expedienteDetail.types";
import { uppercaseInput } from "../../../shared/utils/text";

type Props = {
  label: string;
  value?: string | null;
  placeholder?: string;
  onChange: (value: string) => void;
  onSelect: (interesado: InteresadoSearchResult) => void;
};

export function InteresadoAutocomplete({ label, value, placeholder, onChange, onSelect }: Props) {
  const [open, setOpen] = useState(false);
  const [results, setResults] = useState<InteresadoSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const wrapperRef = useRef<HTMLLabelElement | null>(null);
  const query = value?.trim() || "";

  useEffect(() => {
    if (!open || query.length < 2) {
      setResults([]);
      setLoading(false);
      setError(false);
      return;
    }

    const controller = new AbortController();
    const timeout = window.setTimeout(() => {
      setLoading(true);
      setError(false);
      searchInteresados(query)
        .then((items) => {
          if (!controller.signal.aborted) setResults(items);
        })
        .catch(() => {
          if (!controller.signal.aborted) setError(true);
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
    }, 220);

    return () => {
      controller.abort();
      window.clearTimeout(timeout);
    };
  }, [open, query]);

  useEffect(() => {
    const handlePointerDown = (event: PointerEvent) => {
      if (!wrapperRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("pointerdown", handlePointerDown);
    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, []);

  const select = (interesado: InteresadoSearchResult) => {
    onSelect(interesado);
    setOpen(false);
  };

  return (
    <label className="interesado-autocomplete" ref={wrapperRef}>
      {label}
      <span className="interesado-autocomplete__input">
        <Search size={15} />
        <input
          value={value || ""}
          placeholder={placeholder}
          onFocus={() => setOpen(true)}
          onChange={(event) => {
            onChange(uppercaseInput(event.target.value));
            setOpen(true);
          }}
        />
      </span>
      {open && query.length >= 2 ? (
        <div className="interesado-autocomplete__menu">
          {loading ? <span className="interesado-autocomplete__state">Buscando interesados...</span> : null}
          {error ? <span className="interesado-autocomplete__state">No se pudo buscar ahora.</span> : null}
          {!loading && !error && results.length === 0 ? (
            <span className="interesado-autocomplete__state">Sin coincidencias guardadas.</span>
          ) : null}
          {results.map((interesado) => (
            <button key={interesado.id} type="button" onClick={() => select(interesado)}>
              <UserRoundCheck size={16} />
              <span>
                <strong>{interesado.dni || "SIN DNI"} - {interesado.nombre || "SIN NOMBRE"}</strong>
                <small>
                  {[interesado.telefono, interesado.direccion].filter(Boolean).join(" · ") || interesado.tipoPersona || "INTERESADO GUARDADO"}
                </small>
              </span>
            </button>
          ))}
        </div>
      ) : null}
    </label>
  );
}
