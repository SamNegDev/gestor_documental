import { AlertCircle, Ban, FileQuestion, Home, RefreshCw, ShieldAlert } from "lucide-react";
import { Link, useRouteError } from "react-router-dom";
import "../../features/expedientes/styles/expedienteDetail.css";

type ErrorKind = "denied" | "not-found" | "invalid" | "unexpected";

const errorCopy: Record<ErrorKind, { eyebrow: string; title: string; message: string; icon: typeof AlertCircle }> = {
  denied: {
    eyebrow: "Acceso denegado",
    title: "No tienes permisos para esta vista",
    message: "Tu usuario esta activo, pero no tiene autorizacion para acceder a esta zona.",
    icon: ShieldAlert,
  },
  "not-found": {
    eyebrow: "Recurso no encontrado",
    title: "No hemos encontrado lo que buscas",
    message: "Puede que el recurso ya no exista, que la URL no sea correcta o que no pertenezca a tu cuenta.",
    icon: FileQuestion,
  },
  invalid: {
    eyebrow: "Operacion no permitida",
    title: "Esta accion no esta disponible",
    message: "El estado actual del tramite no permite continuar con esta operacion.",
    icon: Ban,
  },
  unexpected: {
    eyebrow: "Error inesperado",
    title: "No se pudo mostrar esta vista",
    message: "Ha ocurrido un problema inesperado en la aplicacion.",
    icon: AlertCircle,
  },
};

type Props = {
  kind?: ErrorKind;
  message?: string;
};

export function AppErrorPage({ kind = "unexpected", message }: Props) {
  const config = errorCopy[kind];
  const Icon = config.icon;

  return (
    <main className="app-error-page">
      <section className={`app-error-card app-error-card--${kind}`}>
        <span className="app-error-card__icon">
          <Icon size={28} />
        </span>
        <p className="eyebrow">{config.eyebrow}</p>
        <h1>{config.title}</h1>
        <p>{message || config.message}</p>
        <div className="app-error-card__actions">
          <Link className="primary-button" to="/">
            <Home size={16} />
            Ir al inicio
          </Link>
          <button className="soft-button" onClick={() => window.location.reload()} type="button">
            <RefreshCw size={16} />
            Recargar
          </button>
        </div>
      </section>
    </main>
  );
}

export function AppRouteError() {
  const error = useRouteError();
  const message = error instanceof Error ? error.message : undefined;
  return <AppErrorPage kind="unexpected" message={message} />;
}
