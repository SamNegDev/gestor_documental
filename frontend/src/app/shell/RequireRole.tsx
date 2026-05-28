import { Navigate, useLocation, useOutletContext } from "react-router-dom";
import type { ReactNode } from "react";
import type { AppOutletContext } from "./AppLayout";
import { AppErrorPage } from "./AppErrorPage";

type Role = "ADMIN" | "CLIENTE";

type Props = {
  allow: Role[];
  children: ReactNode;
};

export function RequireRole({ allow, children }: Props) {
  const { sessionLoading, user } = useOutletContext<AppOutletContext>();
  const location = useLocation();

  if (sessionLoading) {
    return (
      <div className="exp-detail-state">
        <strong>Comprobando permisos</strong>
        <span>Estamos validando tu sesion antes de mostrar la vista.</span>
      </div>
    );
  }

  if (!user) {
    const next = encodeURIComponent(`${location.pathname}${location.search}`);
    return <Navigate to={`/login?expired=1&next=${next}`} replace />;
  }

  if (!allow.includes(user.rol as Role)) {
    return <AppErrorPage kind="denied" />;
  }

  return children;
}
