import { useEffect, useMemo, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";
import { FilePlus2, FolderOpen, Inbox, LayoutDashboard, LogOut, UserRoundCheck, UsersRound } from "lucide-react";
import { SidebarLink } from "./SidebarLink";
import { Tooltip } from "../../shared/ui/Tooltip";
import { getSessionUser, type SessionUser } from "../../shared/api/sessionApi";
import { logout } from "../../shared/api/authApi";
import { ApiError } from "../../shared/api/http";

export type AppOutletContext = {
  sessionLoading: boolean;
  user: SessionUser | null;
};

function pageTitle(pathname: string) {
  if (pathname.includes("/dashboard")) return "Dashboard";
  if (pathname === "/admin/clientes") return "Clientes";
  if (pathname.includes("/admin/clientes/nuevo")) return "Nuevo cliente";
  if (pathname.includes("/admin/clientes/")) return "Editar cliente";
  if (pathname === "/admin/usuarios") return "Usuarios";
  if (pathname.includes("/admin/usuarios/nuevo")) return "Nuevo usuario";
  if (pathname.includes("/admin/usuarios/")) return "Editar usuario";
  if (pathname === "/expedientes") return "Expedientes";
  if (pathname === "/solicitudes") return "Solicitudes";
  if (pathname.includes("/cliente/solicitudes/") && pathname.endsWith("/editar")) return "Editar solicitud";
  if (pathname.includes("/cliente/solicitudes/nuevo")) return "Nueva solicitud";
  if (pathname.includes("/solicitudes/")) return "Detalle de solicitud";
  if (pathname.includes("/cliente/expedientes/")) return "Estado del expediente";
  if (pathname.endsWith("/editar")) return "Editar expediente";
  if (pathname.endsWith("/nuevo")) return "Nuevo expediente";
  return "Detalle de expediente";
}

export function AppLayout() {
  const location = useLocation();
  const [user, setUser] = useState<SessionUser | null>(null);
  const [sessionLoading, setSessionLoading] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);

  useEffect(() => {
    let mounted = true;
    getSessionUser()
      .then((sessionUser) => {
        if (!mounted) return;
        setUser(sessionUser);
        setSessionExpired(false);
      })
      .catch((cause) => {
        if (!mounted) return;
        setUser(null);
        setSessionExpired(cause instanceof ApiError && cause.status === 401);
      })
      .finally(() => {
        if (mounted) setSessionLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, []);

  const isClientRoute = location.pathname.startsWith("/cliente/") || user?.rol === "CLIENTE";
  const title = useMemo(() => pageTitle(location.pathname), [location.pathname]);
  const roleLabel = user?.rol === "CLIENTE" ? "Cliente" : user?.rol === "ADMIN" ? "Administrador" : "Sesion";

  const handleLogout = async () => {
    await logout().catch(() => undefined);
    window.location.href = "/login?logout=1";
  };

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="Navegacion principal">
        <div className="sidebar__brand">
          <img className="brand-mark brand-mark--image" src="/assets/logos/casado-negrin-symbol.jpg" alt="Casado Negrin Gestoria" />
          <div>
            <strong>Casado Negrin</strong>
            <span>Gestion documental</span>
          </div>
        </div>

        <nav className="sidebar__nav">
          {isClientRoute || user?.rol === "CLIENTE" ? (
            <>
              <SidebarLink to="/cliente/dashboard" icon={LayoutDashboard} label="Dashboard" />
              <SidebarLink to="/expedientes" icon={FolderOpen} label="Mis expedientes" />
              <SidebarLink to="/solicitudes" icon={Inbox} label="Solicitudes" />
            </>
          ) : (
            <>
              <SidebarLink to="/admin/dashboard" icon={LayoutDashboard} label="Dashboard" />
              <SidebarLink to="/expedientes" icon={FolderOpen} label="Expedientes" />
              <SidebarLink to="/expedientes/nuevo" icon={FilePlus2} label="Nuevo expediente" />
              <SidebarLink to="/solicitudes" icon={Inbox} label="Solicitudes" />
              <SidebarLink to="/admin/clientes" icon={UsersRound} label="Clientes" />
              <SidebarLink to="/admin/usuarios" icon={UserRoundCheck} label="Usuarios" />
            </>
          )}
        </nav>
      </aside>

      <div className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">{isClientRoute ? "Portal cliente" : "Gestion interna"}</p>
            <h1>{title}</h1>
          </div>
          <div className="topbar__user">
            <div>
              <strong>{user?.nombreCompleto || (sessionExpired ? "Sesion caducada" : "Usuario")}</strong>
              <span>{roleLabel}</span>
            </div>
            <Tooltip label="Cerrar sesion">
              <button
                type="button"
                className="icon-button"
                aria-label="Cerrar sesion"
                onClick={handleLogout}
              >
                <LogOut size={18} />
              </button>
            </Tooltip>
          </div>
        </header>

        <main className="content">
          <Outlet context={{ sessionLoading, user } satisfies AppOutletContext} />
        </main>
      </div>
    </div>
  );
}
