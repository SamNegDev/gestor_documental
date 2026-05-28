import { Home, IdCard, Phone, UserRound } from "lucide-react";
import type { InteresadoExpediente } from "../types/expedienteDetail.types";
import { humanizeEnum } from "../utils/formatters";

type Props = {
  interesados: InteresadoExpediente[];
};

export function InteresadosPanel({ interesados }: Props) {
  return (
    <section className="exp-panel">
      <div className="exp-panel__heading">
        <div>
          <p className="eyebrow">Datos principales</p>
          <h3>Interesados</h3>
        </div>
        <span className="exp-panel__counter">{interesados.length}</span>
      </div>

      {interesados.length === 0 ? (
        <p className="exp-empty">Todavía no hay interesados asociados a este expediente.</p>
      ) : (
        <div className="interesados-grid">
          {interesados.map((interesado) => (
            <article className="interesado-card" key={interesado.id}>
              <div className="interesado-card__avatar">
                <UserRound size={22} />
              </div>
              <div>
                <strong>{interesado.nombre}</strong>
                <span>{humanizeEnum(interesado.rol)}</span>
              </div>
              <dl>
                <div>
                  <dt>
                    <IdCard size={15} /> DNI/NIE
                  </dt>
                  <dd>{interesado.dni || "No informado"}</dd>
                </div>
                <div>
                  <dt>
                    <Phone size={15} /> Teléfono
                  </dt>
                  <dd>{interesado.telefono || "No informado"}</dd>
                </div>
                <div>
                  <dt>
                    <Home size={15} /> Dirección
                  </dt>
                  <dd>{interesado.direccion || "No informada"}</dd>
                </div>
              </dl>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
