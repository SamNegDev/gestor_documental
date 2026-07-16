import { createBrowserRouter, Navigate } from "react-router-dom";
import { ClienteFormPage } from "../features/admin/pages/ClienteFormPage";
import { ClientesListPage } from "../features/admin/pages/ClientesListPage";
import { UsuarioFormPage } from "../features/admin/pages/UsuarioFormPage";
import { UsuariosListPage } from "../features/admin/pages/UsuariosListPage";
import { AppLayout } from "./shell/AppLayout";
import { AppErrorPage, AppRouteError } from "./shell/AppErrorPage";
import { ClienteExpedientePage } from "../features/expedientes/pages/ClienteExpedientePage";
import { CatalogosGestionPage } from "../features/catalogosGestion/pages/CatalogosGestionPage";
import { DashboardPage } from "../features/listados/pages/DashboardPage";
import { ExpedienteCreatePage } from "../features/expedientes/pages/ExpedienteCreatePage";
import { ExpedienteBulkCreatePage } from "../features/expedientes/pages/ExpedienteBulkCreatePage";
import { ExpedienteDetailPage } from "../features/expedientes/pages/ExpedienteDetailPage";
import { ExpedienteEditPage } from "../features/expedientes/pages/ExpedienteEditPage";
import { ExpedientesListPage } from "../features/listados/pages/ExpedientesListPage";
import { SolicitudDetailPage } from "../features/listados/pages/SolicitudDetailPage";
import { SolicitudBulkCreatePage } from "../features/listados/pages/SolicitudBulkCreatePage";
import { SolicitudFormPage } from "../features/listados/pages/SolicitudFormPage";
import { SolicitudesListPage } from "../features/listados/pages/SolicitudesListPage";
import { LoginPage } from "../features/auth/pages/LoginPage";
import { RequireRole } from "./shell/RequireRole";
import { InteresadosRegistroPage } from "../features/registro/pages/InteresadosRegistroPage";
import { InteresadoRegistroDetailPage } from "../features/registro/pages/InteresadoRegistroDetailPage";
import { VehiculosRegistroPage } from "../features/registro/pages/VehiculosRegistroPage";
import { VehiculoRegistroDetailPage } from "../features/registro/pages/VehiculoRegistroDetailPage";
import { TareasPage } from "../features/tareas/pages/TareasPage";
import { SeguimientoClientesPage } from "../features/seguimiento/pages/SeguimientoClientesPage";
import { SeguimientoConfigPage } from "../features/seguimiento/pages/SeguimientoConfigPage";
import { WhatsappInboxPage } from "../features/whatsapp/pages/WhatsappInboxPage";


export const router = createBrowserRouter([
  {
    path: "/login",
    element: <LoginPage />,
  },
  {
    path: "/",
    element: <AppLayout />,
    errorElement: <AppRouteError />,
    children: [
      { index: true, element: <Navigate to="/expedientes" replace /> },
      { path: "admin/dashboard", element: <RequireRole allow={["ADMIN"]}><DashboardPage /></RequireRole> },
      { path: "admin/tareas", element: <RequireRole allow={["ADMIN"]}><TareasPage /></RequireRole> },
      { path: "admin/seguimiento-clientes", element: <RequireRole allow={["ADMIN"]}><SeguimientoClientesPage /></RequireRole> },
      { path: "admin/seguimiento-config", element: <RequireRole allow={["ADMIN"]}><SeguimientoConfigPage /></RequireRole> },
      { path: "admin/whatsapp", element: <RequireRole allow={["ADMIN"]}><WhatsappInboxPage /></RequireRole> },
      { path: "admin/catalogos-gestion", element: <RequireRole allow={["ADMIN"]}><CatalogosGestionPage /></RequireRole> },
      { path: "cliente/tareas", element: <RequireRole allow={["CLIENTE"]}><TareasPage /></RequireRole> },
      { path: "admin/clientes", element: <RequireRole allow={["ADMIN"]}><ClientesListPage /></RequireRole> },
      { path: "admin/clientes/nuevo", element: <RequireRole allow={["ADMIN"]}><ClienteFormPage /></RequireRole> },
      { path: "admin/clientes/:id/editar", element: <RequireRole allow={["ADMIN"]}><ClienteFormPage /></RequireRole> },
      { path: "admin/usuarios", element: <RequireRole allow={["ADMIN"]}><UsuariosListPage /></RequireRole> },
      { path: "admin/usuarios/nuevo", element: <RequireRole allow={["ADMIN"]}><UsuarioFormPage /></RequireRole> },
      { path: "admin/usuarios/:id/editar", element: <RequireRole allow={["ADMIN"]}><UsuarioFormPage /></RequireRole> },
      { path: "cliente/dashboard", element: <RequireRole allow={["CLIENTE"]}><DashboardPage /></RequireRole> },
      { path: "expedientes", element: <RequireRole allow={["ADMIN", "CLIENTE"]}><ExpedientesListPage /></RequireRole> },
      { path: "expedientes/nuevo", element: <RequireRole allow={["ADMIN"]}><ExpedienteCreatePage /></RequireRole> },
      { path: "expedientes/creacion-multiple", element: <RequireRole allow={["ADMIN"]}><ExpedienteBulkCreatePage /></RequireRole> },
      { path: "expedientes/:id", element: <RequireRole allow={["ADMIN"]}><ExpedienteDetailPage /></RequireRole> },
      { path: "expedientes/:id/editar", element: <RequireRole allow={["ADMIN"]}><ExpedienteEditPage /></RequireRole> },
      { path: "expedientes/:id/proceso", element: <RequireRole allow={["ADMIN"]}><ExpedienteDetailPage /></RequireRole> },
      { path: "cliente/expedientes/:id", element: <RequireRole allow={["CLIENTE"]}><ClienteExpedientePage /></RequireRole> },
      { path: "solicitudes", element: <RequireRole allow={["ADMIN", "CLIENTE"]}><SolicitudesListPage /></RequireRole> },
      { path: "solicitudes/:id", element: <RequireRole allow={["ADMIN", "CLIENTE"]}><SolicitudDetailPage /></RequireRole> },
      { path: "solicitudes/:id/editar", element: <RequireRole allow={["ADMIN"]}><SolicitudFormPage /></RequireRole> },
      { path: "cliente/solicitudes/creacion-multiple", element: <RequireRole allow={["CLIENTE"]}><SolicitudBulkCreatePage /></RequireRole> },
      { path: "interesados", element: <RequireRole allow={["ADMIN", "CLIENTE"]}><InteresadosRegistroPage /></RequireRole> },
      { path: "interesados/:id", element: <RequireRole allow={["ADMIN", "CLIENTE"]}><InteresadoRegistroDetailPage /></RequireRole> },
      { path: "vehiculos", element: <RequireRole allow={["ADMIN", "CLIENTE"]}><VehiculosRegistroPage /></RequireRole> },
      { path: "vehiculos/:matricula", element: <RequireRole allow={["ADMIN", "CLIENTE"]}><VehiculoRegistroDetailPage /></RequireRole> },
      { path: "cliente/solicitudes/nuevo", element: <RequireRole allow={["CLIENTE"]}><SolicitudFormPage /></RequireRole> },
      { path: "cliente/solicitudes/:id/editar", element: <RequireRole allow={["CLIENTE"]}><SolicitudFormPage /></RequireRole> },
      { path: "acceso-denegado", element: <AppErrorPage kind="denied" /> },
      { path: "error", element: <AppErrorPage kind="unexpected" /> },
      { path: "*", element: <AppErrorPage kind="not-found" /> },
    ],
  },
]);
