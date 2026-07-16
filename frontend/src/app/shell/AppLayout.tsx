import { useEffect, useMemo, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";
import { BellRing, CarFront, DatabaseSearch, FolderOpen, FolderPlus, Inbox, LayoutDashboard, LogOut, MessageCircle, Settings2, UserRound, UserRoundCheck, UsersRound, type LucideIcon } from "lucide-react";
import { SidebarLink } from "./SidebarLink";
import { Tooltip } from "../../shared/ui/Tooltip";
import { getSessionUser, type SessionUser } from "../../shared/api/sessionApi";
import { logout } from "../../shared/api/authApi";
import { ApiError } from "../../shared/api/http";
import { useQuery } from "@tanstack/react-query";
import { getTareasResumen } from "../../features/tareas/services/tareasApi";
import { GlobalSearch } from "../../features/busqueda/components/GlobalSearch";
import { clientInitials } from "../../shared/utils/clientBranding";
import { AvisosBell } from "../../features/avisos/components/AvisosBell";

export type AppOutletContext = {
  sessionLoading: boolean;
  user: SessionUser | null;
};

function pageTitle(pathname: string) {
  if (pathname.includes("/dashboard")) return "Dashboard";
  if (pathname === "/admin/tareas") return "Bandeja de tareas";
  if (pathname === "/admin/seguimiento-clientes") return "Seguimiento de clientes";
  if (pathname === "/admin/seguimiento-config") return "Periodos de seguimiento";
  if (pathname === "/admin/whatsapp") return "WhatsApp";
  if (pathname === "/admin/catalogos-gestion") return "Catalogos Gestion Trafico";
  if (pathname === "/cliente/tareas") return "Mis tareas";
  if (pathname === "/admin/clientes") return "Clientes";
  if (pathname.includes("/admin/clientes/nuevo")) return "Nuevo cliente";
  if (pathname.includes("/admin/clientes/")) return "Editar cliente";
  if (pathname === "/admin/usuarios") return "Usuarios";
  if (pathname.includes("/admin/usuarios/nuevo")) return "Nuevo usuario";
  if (pathname.includes("/admin/usuarios/")) return "Editar usuario";
  if (pathname === "/expedientes") return "Expedientes";
  if (pathname === "/expedientes/creacion-multiple") return "Creacion multiple";
  if (pathname === "/solicitudes") return "Solicitudes";
  if (pathname.startsWith("/interesados")) return pathname === "/interesados" ? "Interesados" : "Ficha del interesado";
  if (pathname.startsWith("/vehiculos")) return pathname === "/vehiculos" ? "Vehiculos" : "Ficha del vehiculo";
  if (pathname.includes("/cliente/solicitudes/") && pathname.endsWith("/editar")) return "Editar solicitud";
  if (pathname === "/cliente/solicitudes/creacion-multiple") return "Creacion multiple";
  if (pathname.includes("/cliente/solicitudes/nuevo")) return "Nueva solicitud";
  if (pathname.includes("/solicitudes/")) return "Detalle de solicitud";
  if (pathname.includes("/cliente/expedientes/")) return "Estado del expediente";
  if (pathname.endsWith("/editar")) return "Editar expediente";
  if (pathname.endsWith("/nuevo")) return "Nuevo expediente";
  return "Detalle de expediente";
}

type MenuItemConfig = {
  id: string;
  to: string;
  icon: LucideIcon;
  label: string;
  badge?: number;
};

const adminMenuItems: MenuItemConfig[] = [
  { id: "dashboard", to: "/admin/dashboard", icon: LayoutDashboard, label: "Dashboard" },
  { id: "tareas", to: "/admin/tareas", icon: Inbox, label: "Tareas" },
  { id: "seguimiento", to: "/admin/seguimiento-clientes", icon: BellRing, label: "Seguimiento clientes" },
  { id: "whatsapp", to: "/admin/whatsapp", icon: MessageCircle, label: "WhatsApp" },
  { id: "expedientes", to: "/expedientes", icon: FolderOpen, label: "Expedientes" },
  { id: "expedientes-bulk", to: "/expedientes/creacion-multiple", icon: FolderPlus, label: "Creacion multiple" },
  { id: "solicitudes", to: "/solicitudes", icon: Inbox, label: "Solicitudes" },
  { id: "interesados", to: "/interesados", icon: UserRound, label: "Interesados" },
  { id: "vehiculos", to: "/vehiculos", icon: CarFront, label: "Vehiculos" },
  { id: "clientes", to: "/admin/clientes", icon: UsersRound, label: "Clientes" },
  { id: "usuarios", to: "/admin/usuarios", icon: UserRoundCheck, label: "Usuarios" },
];

const adminSettingsItems: MenuItemConfig[] = [
  { id: "seguimiento-config", to: "/admin/seguimiento-config", icon: BellRing, label: "Avisos" },
  { id: "catalogos-gestion", to: "/admin/catalogos-gestion", icon: DatabaseSearch, label: "Datos auxiliares" },
];

const clientMenuItems: MenuItemConfig[] = [
  { id: "dashboard", to: "/cliente/dashboard", icon: LayoutDashboard, label: "Dashboard" },
  { id: "tareas", to: "/cliente/tareas", icon: Inbox, label: "Mis tareas" },
  { id: "expedientes", to: "/expedientes", icon: FolderOpen, label: "Mis expedientes" },
  { id: "solicitudes", to: "/solicitudes", icon: Inbox, label: "Solicitudes" },
  { id: "solicitudes-bulk", to: "/cliente/solicitudes/creacion-multiple", icon: FolderPlus, label: "Creacion multiple" },
  { id: "interesados", to: "/interesados", icon: UserRound, label: "Interesados" },
  { id: "vehiculos", to: "/vehiculos", icon: CarFront, label: "Vehiculos" },
];

const fixedMenuItems = new Set(["dashboard", "tareas"]);

function menuStorageKey(user: SessionUser | null) {
  return user ? `gestor.menu.${user.id}.${user.rol || "USER"}` : "gestor.menu.guest";
}

export function AppLayout() {
  const location = useLocation();
  const [user, setUser] = useState<SessionUser | null>(null);
  const [sessionLoading, setSessionLoading] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);
  const [menuSettingsOpen, setMenuSettingsOpen] = useState(false);
  const [hiddenMenuItems, setHiddenMenuItems] = useState<string[]>([]);

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
  const roleLabel = user?.rol === "CLIENTE" ? "Cliente" : user?.rol === "ADMIN" ? "Administrador" : "Sesión";
  const clientBrandImage = user?.cliente?.logoPrincipalUrl || user?.cliente?.logoCompactoUrl;
  const tareasResumen = useQuery({
    queryKey: ["tareas", "resumen", "menu", user?.rol],
    queryFn: getTareasResumen,
    enabled: Boolean(user),
    refetchInterval: 30000,
    refetchOnMount: "always",
    refetchOnWindowFocus: "always",
  });
  useEffect(() => {
    if (!user) return;
    try {
      const saved = window.localStorage.getItem(menuStorageKey(user));
      setHiddenMenuItems(saved ? JSON.parse(saved) as string[] : []);
    } catch {
      setHiddenMenuItems([]);
    }
  }, [user]);

  const baseMenuItems = user?.rol === "CLIENTE" || isClientRoute ? clientMenuItems : adminMenuItems;
  const settingsItems = user?.rol === "ADMIN" && !isClientRoute ? adminSettingsItems : [];
  const menuItems = baseMenuItems.map((item) => ({
    ...item,
    badge: item.id === "tareas" ? tareasResumen.data?.total : undefined,
  }));
  const visibleMenuItems = menuItems.filter((item) => !hiddenMenuItems.includes(item.id));
  const toggleMenuItem = (id: string) => {
    if (!user || fixedMenuItems.has(id)) return;
    const next = hiddenMenuItems.includes(id) ? hiddenMenuItems.filter((item) => item !== id) : [...hiddenMenuItems, id];
    setHiddenMenuItems(next);
    window.localStorage.setItem(menuStorageKey(user), JSON.stringify(next));
  };

  const handleLogout = async () => {
    await logout().catch(() => undefined);
    window.location.href = "/login?logout=1";
  };

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="Navegacion principal">
        <div className={`sidebar__brand ${user?.rol === "CLIENTE" ? "sidebar__brand--client" : ""}`}>
          {user?.rol === "CLIENTE" ? (
            clientBrandImage ? (
              <img className="sidebar-client-logo" src={clientBrandImage} alt={`Logo de ${user.cliente?.nombre || "cliente"}`} />
            ) : (
              <span className="brand-mark sidebar-client-initials" aria-hidden="true">{clientInitials(user.cliente?.nombre)}</span>
            )
          ) : (
            <img className="brand-mark brand-mark--image" src="/assets/logos/casado-negrin-symbol.jpg" alt="Casado Negrín Gestoría" />
          )}
          <div className="topbar__title">
            <strong>{user?.rol === "CLIENTE" ? user.cliente?.nombre || "Portal cliente" : "Casado Negrín"}</strong>
            <span>{user?.rol === "CLIENTE" ? "Portal documental" : "Gestión documental"}</span>
          </div>
        </div>

        <nav className="sidebar__nav">
          {visibleMenuItems.map((item) => <SidebarLink key={item.id} to={item.to} icon={item.icon} label={item.label} badge={item.badge} />)}
        </nav>
        {user ? (
          <div className="sidebar-menu-settings">
            <button className="sidebar-menu-settings__trigger" onClick={() => setMenuSettingsOpen((open) => !open)} type="button">
              <Settings2 size={15} />
              Configuracion
            </button>
            {menuSettingsOpen ? (
              <div className="sidebar-menu-settings__panel">
                {settingsItems.length ? (
                  <div className="sidebar-menu-settings__links">
                    {settingsItems.map((item) => <SidebarLink key={item.id} to={item.to} icon={item.icon} label={item.label} />)}
                  </div>
                ) : null}
                <div className="sidebar-menu-settings__section-title">Vista</div>
                {menuItems.map((item) => (
                  <label key={item.id}>
                    <input checked={!hiddenMenuItems.includes(item.id)} disabled={fixedMenuItems.has(item.id)} onChange={() => toggleMenuItem(item.id)} type="checkbox" />
                    <span>{item.label}</span>
                  </label>
                ))}
              </div>
            ) : null}
          </div>
        ) : null}
      </aside>

      <div className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">{isClientRoute ? "Portal cliente" : "Gestión interna"}</p>
            <h1>{title}</h1>
          </div>
          <GlobalSearch />
          <div className="topbar__user">
            {user?.rol === "ADMIN" ? <AvisosBell /> : null}
            <div>
              <strong>{user?.nombreCompleto || (sessionExpired ? "Sesión caducada" : "Usuario")}</strong>
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
