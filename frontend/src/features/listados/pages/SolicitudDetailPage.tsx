import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useOutletContext, useParams } from "react-router-dom";
import { AlertTriangle, ArrowLeft, CarFront, CheckCircle2, FileSignature, FileText, FolderCheck, IdCard, Info, Loader2, MapPin, MessageSquare, Pencil, Phone, RefreshCw, RotateCcw, Scissors, Send, Sparkles, UserPlus, UserRound, X } from "lucide-react";
import { StatusBadge } from "../../../shared/ui/StatusBadge";
import { useConfirmDialog } from "../../../shared/ui/ConfirmDialog";
import { ApiError } from "../../../shared/api/http";
import { uppercaseInput } from "../../../shared/utils/text";
import type { AppOutletContext } from "../../../app/shell/AppLayout";
import { CompleteExpedienteUploadPanel } from "../../expedientes/components/CompleteExpedienteUploadPanel";
import { DocumentTemplateDialog } from "../../expedientes/components/DocumentTemplateDialog";
import { OcrReviewDialog } from "../../expedientes/components/OcrReviewDialog";
import {
  deleteDocument,
  deleteDocumentPages,
  extractDocumentPages,
  getCompleteExpedienteProcessing,
  mergeDocuments,
  readDocumentIdentity,
  startCompleteSolicitudProcessing,
  updateDocument,
} from "../../expedientes/services/documentosApi";
import type { DocumentoIdentidadDetectada, DocumentoIdentidadLectura, DocumentoExpediente, ProcesamientoExpedienteCompleto } from "../../expedientes/types/expedienteDetail.types";
import { formatDocumentType } from "../../expedientes/utils/formatters";
import "../../expedientes/styles/expedienteDetail.css";
import {
  asignarInteresadoHabitualSolicitud,
  anadirIdentidadDetectadaSolicitud,
  cambiarEstadoSolicitud,
  convertirSolicitud,
  enviarMensajeSolicitud,
  getSolicitudInteresadosHabituales,
  getSolicitudInteresadoCoincidencias,
  getSolicitudDetail,
  getSolicitudPreparacionTraspaso,
  procesarSolicitudDocumentacionIa,
  procesarSolicitudDocumentacionIaCliente,
  resetSolicitudDatosIa,
} from "../services/listadosApi";
import type {
  InteresadoSolicitud,
  LecturaIaSolicitudCliente,
  SolicitudDetail,
  SolicitudDocumentacionIaResponse,
  SolicitudIdentidadDetectadaInput,
  SolicitudInteresadoHabitual,
  SolicitudPreparacionBloque,
  SolicitudPreparacionDocumento,
  SolicitudPreparacionTraspaso,
} from "../types";
const COMPLETE_SOLICITUD_JOB_STORAGE_PREFIX = "gestor.solicitudCompleta.job.";
const COMPLETE_DOCUMENT_POLL_TIMEOUT_MS = 15 * 60 * 1000;
const SOLICITUD_ROLES = ["COMPRADOR", "VENDEDOR", "COMPRAVENTA", "TITULAR"];

export function SolicitudDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useOutletContext<AppOutletContext>();
  const [mensaje, setMensaje] = useState("");
  const [ocrReviewOpen, setOcrReviewOpen] = useState(false);
  const [ocrReviewDocuments, setOcrReviewDocuments] = useState<DocumentoExpediente[]>([]);
  const [completeSolicitudProcessing, setCompleteSolicitudProcessing] = useState(false);
  const [completeSolicitudJob, setCompleteSolicitudJob] = useState<ProcesamientoExpedienteCompleto | null>(null);
  const [completeSolicitudMinimized, setCompleteSolicitudMinimized] = useState(false);
  const [checkingInteresados, setCheckingInteresados] = useState(false);
  const [iaResult, setIaResult] = useState<SolicitudDocumentacionIaResponse | null>(null);
  const [iaError, setIaError] = useState<string | null>(null);
  const [templateDialogOpen, setTemplateDialogOpen] = useState(false);
  const [habitualModalOpen, setHabitualModalOpen] = useState(false);
  const [habitualSearch, setHabitualSearch] = useState("");
  const { confirm, dialog } = useConfirmDialog();
  const isAdmin = user?.rol === "ADMIN";

  const solicitudQuery = useQuery({
    queryKey: ["solicitudes", "detalle", id],
    queryFn: () => getSolicitudDetail(id!),
    enabled: Boolean(id),
  });

  const preparacionQuery = useQuery({
    queryKey: ["solicitudes", "preparacion-traspaso", id],
    queryFn: () => getSolicitudPreparacionTraspaso(id!),
    enabled: Boolean(id),
  });

  const habitualesQuery = useQuery({
    queryKey: ["solicitudes", "habituales", id, habitualSearch],
    queryFn: () => getSolicitudInteresadosHabituales(id!, habitualSearch),
    enabled: Boolean(id),
  });

  const convertirMutation = useMutation({
    mutationFn: (solicitudId: number) => convertirSolicitud(solicitudId),
    onSuccess: async (expediente) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["solicitudes"] }),
        queryClient.invalidateQueries({ queryKey: ["expedientes"] }),
        queryClient.invalidateQueries({ queryKey: ["tareas"] }),
        queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
        queryClient.invalidateQueries({ queryKey: ["registro"] }),
      ]);
      navigate(`/expedientes/${expediente.id}`);
    },
  });

  const estadoMutation = useMutation({
    mutationFn: ({ solicitudId, estado }: { solicitudId: number; estado: string }) => cambiarEstadoSolicitud(solicitudId, estado),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["solicitudes"] }),
        queryClient.invalidateQueries({ queryKey: ["tareas"] }),
        queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
      ]);
    },
  });

  const mensajeMutation = useMutation({
    mutationFn: ({ solicitudId, contenido }: { solicitudId: number; contenido: string }) => enviarMensajeSolicitud(solicitudId, contenido),
    onSuccess: () => {
      setMensaje("");
      queryClient.invalidateQueries({ queryKey: ["solicitudes", "detalle", id] });
      queryClient.invalidateQueries({ queryKey: ["solicitudes", "preparacion-traspaso", id] });
    },
  });

  const procesarDocumentacionMutation = useMutation({
    mutationFn: ({ solicitudId, forzarRelectura }: { solicitudId: number; forzarRelectura?: boolean }) =>
      procesarSolicitudDocumentacionIa(solicitudId, { forzarRelectura }),
  });

  const procesarDocumentacionClienteMutation = useMutation({
    mutationFn: (solicitudId: number) => procesarSolicitudDocumentacionIaCliente(solicitudId),
  });

  const resetDatosIaMutation = useMutation({
    mutationFn: (solicitudId: number) => resetSolicitudDatosIa(solicitudId),
    onSuccess: async (actualizada) => {
      queryClient.setQueryData(["solicitudes", "detalle", id], actualizada);
      setIaResult(null);
      setIaError(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["solicitudes"] }),
        queryClient.invalidateQueries({ queryKey: ["solicitudes", "preparacion-traspaso", id] }),
        queryClient.invalidateQueries({ queryKey: ["tareas"] }),
        queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
      ]);
    },
  });

  const anadirIdentidadDetectadaMutation = useMutation({
    mutationFn: ({ solicitudId, input }: { solicitudId: number; input: SolicitudIdentidadDetectadaInput }) =>
      anadirIdentidadDetectadaSolicitud(solicitudId, input),
    onSuccess: async (actualizada) => {
      queryClient.setQueryData(["solicitudes", "detalle", id], actualizada);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["solicitudes"] }),
        queryClient.invalidateQueries({ queryKey: ["solicitudes", "preparacion-traspaso", id] }),
        queryClient.invalidateQueries({ queryKey: ["tareas"] }),
        queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
      ]);
    },
  });

  const releerIdentidadMutation = useMutation({
    mutationFn: (documentoId: number) => readDocumentIdentity(documentoId, true),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["solicitudes", "detalle", id] }),
        queryClient.invalidateQueries({ queryKey: ["solicitudes", "preparacion-traspaso", id] }),
        queryClient.invalidateQueries({ queryKey: ["tareas"] }),
        queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
      ]);
    },
  });

  const asignarHabitualMutation = useMutation({
    mutationFn: ({ solicitudId, input }: { solicitudId: number; input: { interesadoId: number; rol: string } }) =>
      asignarInteresadoHabitualSolicitud(solicitudId, input),
    onSuccess: async (actualizada) => {
      queryClient.setQueryData(["solicitudes", "detalle", id], actualizada);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["solicitudes"] }),
        queryClient.invalidateQueries({ queryKey: ["solicitudes", "preparacion-traspaso", id] }),
        queryClient.invalidateQueries({ queryKey: ["tareas"] }),
        queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
      ]);
    },
  });

  const refreshSolicitud = async () => {
    const result = await solicitudQuery.refetch();
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["solicitudes"] }),
      queryClient.invalidateQueries({ queryKey: ["solicitudes", "preparacion-traspaso", id] }),
      queryClient.invalidateQueries({ queryKey: ["tareas"] }),
      queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
    ]);
    return result.data;
  };

  useEffect(() => {
    const documentos = solicitudQuery.data?.documentos ?? [];
    const hasCompleteDocument = documentos.some((documento) => documento.tipo === "EXPEDIENTE_COMPLETO");
    const hasSeparatedDocuments = documentos.some((documento) => documento.tipo !== "EXPEDIENTE_COMPLETO");
    if (!id || !hasCompleteDocument || hasSeparatedDocuments || completeSolicitudProcessing) return;

    const startedAt = Date.now();
    const intervalId = window.setInterval(() => {
      if (Date.now() - startedAt > COMPLETE_DOCUMENT_POLL_TIMEOUT_MS) {
        window.clearInterval(intervalId);
        return;
      }
      void refreshSolicitud();
    }, 5000);

    return () => window.clearInterval(intervalId);
  }, [completeSolicitudProcessing, id, solicitudQuery.data?.documentos]);

  useEffect(() => {
    if (!id) return;
    const storedJobId = window.localStorage.getItem(`${COMPLETE_SOLICITUD_JOB_STORAGE_PREFIX}${id}`);
    if (!storedJobId) return;
    getCompleteExpedienteProcessing(storedJobId)
      .then((job) => {
        setCompleteSolicitudJob(job);
        setCompleteSolicitudProcessing(job.estado === "PENDIENTE" || job.estado === "PROCESANDO");
        if (job.estado === "COMPLETADO" || job.estado === "ERROR") {
          window.localStorage.removeItem(`${COMPLETE_SOLICITUD_JOB_STORAGE_PREFIX}${id}`);
        }
      })
      .catch(() => window.localStorage.removeItem(`${COMPLETE_SOLICITUD_JOB_STORAGE_PREFIX}${id}`));
  }, [id]);

  useEffect(() => {
    if (!completeSolicitudJob || !id) return;
    if (completeSolicitudJob.estado !== "PENDIENTE" && completeSolicitudJob.estado !== "PROCESANDO") return;
    const intervalId = window.setInterval(() => {
      getCompleteExpedienteProcessing(completeSolicitudJob.jobId)
        .then(async (job) => {
          setCompleteSolicitudJob(job);
          const active = job.estado === "PENDIENTE" || job.estado === "PROCESANDO";
          setCompleteSolicitudProcessing(active);
          if (!active) {
            window.localStorage.removeItem(`${COMPLETE_SOLICITUD_JOB_STORAGE_PREFIX}${id}`);
            await refreshSolicitud();
          }
        })
        .catch(() => {
          setCompleteSolicitudProcessing(false);
          window.localStorage.removeItem(`${COMPLETE_SOLICITUD_JOB_STORAGE_PREFIX}${id}`);
        });
    }, 2500);
    return () => window.clearInterval(intervalId);
  }, [completeSolicitudJob, id]);

  const handleUploadCompleteSolicitud = async (archivo: File) => {
    const solicitud = solicitudQuery.data;
    if (!solicitud) return;
    setCompleteSolicitudProcessing(true);
    setCompleteSolicitudMinimized(false);
    try {
      const job = await startCompleteSolicitudProcessing(solicitud.id, archivo);
      setCompleteSolicitudJob(job);
      window.localStorage.setItem(`${COMPLETE_SOLICITUD_JOB_STORAGE_PREFIX}${solicitud.id}`, job.jobId);
      await refreshSolicitud();
    } catch {
      setCompleteSolicitudProcessing(false);
      alert("No se pudo iniciar la separacion del PDF completo de la solicitud.");
    }
  };

  const handleDeleteDocument = async (documento: DocumentoExpediente) => {
    if (!documento.id) return;
    const confirmed = await confirm({
      title: "Borrar documento",
      description: `Se eliminara ${documento.nombreOriginal || documento.nombre}. Esta operacion no se puede deshacer.`,
      confirmLabel: "Borrar",
      tone: "danger",
    });
    if (!confirmed) return;
    try {
      await deleteDocument(documento.id);
      await refreshSolicitud();
      setOcrReviewDocuments((current) => current.filter((item) => item.id !== documento.id));
    } catch {
      alert("No se pudo borrar el documento.");
    }
  };

  const handleSaveOcrDocument = async (documento: DocumentoExpediente, tipoDocumento: string) => {
    if (!documento.id) return;
    try {
      await updateDocument(documento.id, tipoDocumento, undefined, undefined, true);
      const actualizada = await refreshSolicitud();
      if (actualizada) {
        setOcrReviewDocuments((current) =>
          current.map((item) => actualizada.documentos.find((updated) => updated.id === item.id) ?? item),
        );
      }
    } catch {
      alert("No se pudo editar el documento.");
    }
  };

  const handleDeleteOcrPages = async (documento: DocumentoExpediente, rangoPaginas: string) => {
    if (!documento.id) return;
    try {
      await deleteDocumentPages(documento.id, rangoPaginas);
      const actualizada = await refreshSolicitud();
      if (actualizada) {
        setOcrReviewDocuments((current) =>
          current.map((item) => actualizada.documentos.find((updated) => updated.id === item.id) ?? item),
        );
      }
    } catch {
      alert("No se pudieron borrar las paginas.");
    }
  };

  const handleMergeOcrDocuments = async (
    documento: DocumentoExpediente,
    documentoIds: number[],
    tipoDocumento: string,
    nombreSinExtension: string,
  ) => {
    if (!documento.id) return;
    try {
      await mergeDocuments(documento.id, documentoIds, tipoDocumento, nombreSinExtension);
      const actualizada = await refreshSolicitud();
      if (actualizada) {
        setOcrReviewDocuments((current) =>
          current
            .map((item) => (item.id ? actualizada.documentos.find((updated) => updated.id === item.id) : item))
            .filter((item): item is DocumentoExpediente => Boolean(item)),
        );
      }
    } catch {
      alert("No se pudieron juntar los documentos.");
    }
  };

  const handleExtractOcrPages = async (
    documento: DocumentoExpediente,
    rangoPaginas: string,
    tipoDocumento: string,
  ) => {
    if (!documento.id) return;
    try {
      const previousIds = new Set(solicitudQuery.data?.documentos.map((item) => item.id).filter(Boolean));
      await extractDocumentPages(documento.id, rangoPaginas, tipoDocumento);
      const actualizada = await refreshSolicitud();
      if (actualizada) {
        setOcrReviewDocuments((current) => {
          const updatedCurrent = current
            .map((item) => (item.id ? actualizada.documentos.find((updated) => updated.id === item.id) : item))
            .filter((item): item is DocumentoExpediente => Boolean(item));
          const nuevos = actualizada.documentos.filter((item) => item.id && !previousIds.has(item.id));
          return [...updatedCurrent, ...nuevos];
        });
      }
    } catch {
      alert("No se pudieron separar las paginas.");
    }
  };

  const handleOpenDocumentReview = () => {
    setOcrReviewDocuments(solicitudQuery.data?.documentos.filter((documento) => documento.id) ?? []);
    setOcrReviewOpen(true);
  };

  const handleConvertSolicitud = async () => {
    const solicitudActual = solicitudQuery.data;
    if (!solicitudActual) return;
    setCheckingInteresados(true);
    try {
      const coincidencias = await getSolicitudInteresadoCoincidencias(solicitudActual.id);
      if (coincidencias.length > 0) {
        const confirmed = await confirm({
          title: "Interesado ya registrado",
          description: buildCoincidenciasDescription(coincidencias),
          confirmLabel: "Usar datos registrados",
          cancelLabel: "Revisar solicitud",
          tone: "default",
        });
        if (!confirmed) return;
      }
      convertirMutation.mutate(solicitudActual.id);
    } catch {
      alert("No se pudo comprobar si los interesados ya estaban registrados.");
    } finally {
      setCheckingInteresados(false);
    }
  };

  const handleProcessDocumentacionIa = async (forzarRelectura = false) => {
    const solicitudActual = solicitudQuery.data;
    if (!solicitudActual) return;
    const confirmed = await confirm({
      title: forzarRelectura ? "Releer documentacion con IA" : "Procesar documentacion",
      description: forzarRelectura
        ? "Se volveran a leer DNI/CIF y contratos/facturas aunque ya tengan lectura previa. Las nuevas lecturas sustituiran a las anteriores para recalcular comprador y vendedor."
        : solicitudActual.tipoTramite === "BATECOM"
          ? "Se leeran DNI/CIF y contratos/facturas. El sistema buscara la compraventa que aparece como comprador en una operacion y vendedor en otra."
          : "Se leeran solo los DNI/CIF y factura/contrato que no tengan lectura previa. Si ya hay una lectura correcta se reutilizara para actualizar comprador y vendedor.",
      confirmLabel: forzarRelectura ? "Releer" : "Procesar",
      cancelLabel: "Cancelar",
      tone: "default",
    });
    if (!confirmed) return;
    try {
      setIaResult(null);
      setIaError(null);
      const response = await procesarDocumentacionMutation.mutateAsync({ solicitudId: solicitudActual.id, forzarRelectura });
      await refreshSolicitud();
      setIaResult(response);
    } catch (cause) {
      setIaError(cause instanceof ApiError ? cause.details || "No se pudo procesar la documentacion." : "No se pudo procesar la documentacion.");
    }
  };

  const handleProcessClienteIa = async () => {
    const solicitudActual = solicitudQuery.data;
    if (!solicitudActual) return;
    const confirmed = await confirm({
      title: "Solicitar lectura IA",
      description: "Se revisara la documentacion aportada con IA. Puedes volver a solicitar lectura si aportas nueva documentacion o corriges datos.",
      confirmLabel: "Solicitar",
      cancelLabel: "Cancelar",
      tone: "default",
    });
    if (!confirmed) return;
    try {
      setIaResult(null);
      setIaError(null);
      const response = await procesarDocumentacionClienteMutation.mutateAsync(solicitudActual.id);
      await refreshSolicitud();
      setIaResult(response);
    } catch (cause) {
      setIaError(cause instanceof ApiError ? cause.details || "No se pudo procesar la documentacion." : "No se pudo procesar la documentacion.");
    }
  };

  const handleResetDatosIa = async () => {
    const solicitudActual = solicitudQuery.data;
    if (!solicitudActual) return;
    const confirmed = await confirm({
      title: "Resetear datos IA",
      description: "Se vaciaran interesados y datos de vehiculo guardados en la solicitud. No se borraran documentos, mensajes ni la matricula.",
      confirmLabel: "Resetear",
      cancelLabel: "Cancelar",
      tone: "danger",
    });
    if (!confirmed) return;
    try {
      await resetDatosIaMutation.mutateAsync(solicitudActual.id);
    } catch (cause) {
      alert(cause instanceof ApiError ? cause.details || "No se pudieron resetear los datos IA." : "No se pudieron resetear los datos IA.");
    }
  };

  const handleAddDetectedIdentity = async (documento: DocumentoExpediente, identidad: DocumentoIdentidadDetectada, rol: string, identificador: string) => {
    const solicitudActual = solicitudQuery.data;
    if (!solicitudActual) return;
    if (!rol) {
      alert("Selecciona el rol antes de anadir la identidad.");
      return;
    }
    const identificadorNormalizado = normalizeIdentityIdentifier(identificador);
    if (!identificadorNormalizado) {
      alert("Revisa el DNI/NIE/CIF antes de anadir la identidad.");
      return;
    }
    try {
      await anadirIdentidadDetectadaMutation.mutateAsync({
        solicitudId: solicitudActual.id,
        input: {
          documentoId: documento.id,
          rol,
          tipoDocumentoDetectado: identidad.tipoDocumentoDetectado,
          identificador: identificadorNormalizado,
          identificadorOriginal: identidad.identificador,
          nombre: identidad.nombre,
          apellido1: identidad.apellido1,
          apellido2: identidad.apellido2,
          razonSocial: identidad.razonSocial,
          nombreCompleto: identidad.nombreCompleto,
          fechaNacimiento: identidad.fechaNacimiento,
          fechaCaducidad: identidad.fechaCaducidad,
          direccionTexto: identidad.direccionTexto,
        },
      });
    } catch (cause) {
      alert(cause instanceof ApiError ? cause.details || "No se pudo anadir la identidad." : "No se pudo anadir la identidad.");
    }
  };

  const handleRereadIdentity = async (documento: DocumentoExpediente) => {
    const confirmed = await confirm({
      title: "Releer identidad",
      description: "Se sustituira la lectura de identidad guardada para este PDF. Si contiene dos DNI/CIF, la nueva lectura deberia mostrar ambas opciones para asignarlas.",
      confirmLabel: "Releer",
      cancelLabel: "Cancelar",
    });
    if (!confirmed) return;
    try {
      await releerIdentidadMutation.mutateAsync(documento.id);
    } catch (cause) {
      alert(cause instanceof ApiError ? cause.details || "No se pudo releer la identidad." : "No se pudo releer la identidad.");
    }
  };

  const handleAssignHabitual = async (habitual: SolicitudInteresadoHabitual, rol: string) => {
    const solicitudActual = solicitudQuery.data;
    if (!solicitudActual) return;
    if (!rol) {
      alert("Selecciona el rol antes de asignar el cliente habitual.");
      return;
    }
    try {
      await asignarHabitualMutation.mutateAsync({
        solicitudId: solicitudActual.id,
        input: { interesadoId: habitual.id, rol },
      });
    } catch (cause) {
      alert(cause instanceof ApiError ? cause.details || "No se pudo asignar el cliente habitual." : "No se pudo asignar el cliente habitual.");
    }
  };

  if (solicitudQuery.isLoading) {
    return <div className="records-empty">Cargando solicitud...</div>;
  }

  if (solicitudQuery.error || !solicitudQuery.data) {
    return <div className="records-empty records-empty--danger">No se ha podido cargar la solicitud.</div>;
  }

  const solicitud = solicitudQuery.data;
  const isClosed = solicitud.estado === "CONVERTIDA" || solicitud.estado === "RECHAZADO";
  const interesadosVisibles = solicitud.interesados.filter(hasInteresadoData);
  const existingSolicitudIdentities = solicitud.interesados
    .reduce<SolicitudExistingIdentity[]>((result, interesado) => {
      const identificador = normalizeIdentityIdentifier(interesado.dni);
      if (identificador) {
        result.push({ identificador, rol: interesado.rol, nombre: interesado.nombre });
      }
      return result;
    }, []);
  const existingIdentityIdentifiers = new Set(existingSolicitudIdentities.map((identity) => identity.identificador));
  const hasSolicitudDocuments = solicitud.documentos.some((documento) => documento.id);
  const editSolicitudPath = isAdmin ? `/solicitudes/${solicitud.id}/editar` : `/cliente/solicitudes/${solicitud.id}/editar`;
  const preparationTitle = solicitud.tipoTramite === "TRASPASO" ? "Preparacion del traspaso" : "Preparacion de la solicitud";
  const preparationIaPending = isAdmin ? procesarDocumentacionMutation.isPending : procesarDocumentacionClienteMutation.isPending;
  const preparationIaDisabled = isAdmin
    ? procesarDocumentacionMutation.isPending || !hasSolicitudDocuments
    : procesarDocumentacionClienteMutation.isPending || !solicitud.lecturaIaCliente?.puedeSolicitar;
  const preparationIaLabel = isAdmin
    ? (procesarDocumentacionMutation.isPending ? "Leyendo IA" : "Lectura IA")
    : clienteIaButtonText(solicitud.lecturaIaCliente, procesarDocumentacionClienteMutation.isPending);
  const vehiculo = solicitud.vehiculo;
  const vehicleSummaryFacts = [
    { label: "Marca", value: vehiculo?.marca },
    { label: "Modelo", value: vehiculo?.modelo },
    { label: "Bastidor", value: vehiculo?.bastidor },
  ];
  const vehicleDataComplete = Boolean(vehiculo?.marca && vehiculo?.modelo && vehiculo?.bastidor);

  return (
    <section className="request-page">
      <div className="request-sheet">
        <div className="request-hero">
          <div className="request-hero__identity">
            <p className="eyebrow">{isAdmin ? "Revision de solicitud" : "Seguimiento de solicitud"}</p>
            <div className="case-title-row request-title-row">
              <MiniPlate value={solicitud.matricula} />
              <div>
                <h2>Solicitud #{solicitud.id}</h2>
                <p>{solicitud.tipoTramite || "Sin tipo de tramite"}</p>
              </div>
            </div>
          </div>
          <div className="request-hero__actions">
            <StatusBadge tone={statusTone(solicitud.estado)}>{formatEnum(solicitud.estado)}</StatusBadge>
            {solicitud.expedienteId ? (
              <Link className="soft-button soft-button--compact" to={isAdmin ? `/expedientes/${solicitud.expedienteId}` : `/cliente/expedientes/${solicitud.expedienteId}`}>
                <FolderCheck size={16} />
                Ver expediente
              </Link>
            ) : null}
            <Link className="soft-button soft-button--compact" to="/solicitudes">
              <ArrowLeft size={16} />
              Volver
            </Link>
            {!isAdmin && !isClosed ? (
              <button className="soft-button soft-button--compact" onClick={() => setTemplateDialogOpen(true)} type="button">
                <FileSignature size={16} />
                Generar docs
              </button>
            ) : null}
            {!isAdmin && !isClosed ? (
              <Link className="soft-button soft-button--compact" to={`/cliente/solicitudes/${solicitud.id}/editar`}>
                <Pencil size={16} />
                Editar
              </Link>
            ) : null}
          </div>
        </div>

        <div className="request-summary-strip">
          <section className="request-summary-block request-summary-block--vehicle" aria-labelledby="solicitud-vehiculo-title">
            <div className="request-summary-block__head">
              <span className="row-icon" aria-hidden="true">
                <CarFront size={16} />
              </span>
              <div>
                <h3 id="solicitud-vehiculo-title">Vehiculo</h3>
                <p>{vehicleDataComplete ? "Datos listos" : "Faltan datos"}</p>
              </div>
              {!isClosed ? (
                <Link className="icon-button request-summary-block__action" to={editSolicitudPath} aria-label="Editar vehiculo" title="Editar vehiculo">
                  <Pencil size={15} />
                </Link>
              ) : null}
            </div>
            <dl className="request-summary-facts">
              {vehicleSummaryFacts.map((item) => (
                <div key={item.label}>
                  <dt>{item.label}</dt>
                  <dd>{item.value || "No consta"}</dd>
                </div>
              ))}
            </dl>
          </section>

          <section className="request-summary-block request-summary-block--people" aria-labelledby="solicitud-interesados-title">
            <div className="request-summary-block__head">
              <span className="row-icon" aria-hidden="true">
                <UserRound size={16} />
              </span>
              <div>
                <h3 id="solicitud-interesados-title">Interesados</h3>
                <p>{interesadosVisibles.length > 0 ? `${interesadosVisibles.length} registrados` : "Pendientes"}</p>
              </div>
              {!isClosed ? (
                <div className="request-summary-actions">
                  <button className="icon-button" onClick={() => setHabitualModalOpen(true)} type="button" aria-label="Asignar habitual" title="Habituales">
                    <UserPlus size={15} />
                  </button>
                  <Link className="icon-button" to={editSolicitudPath} aria-label="Revisar interesados" title="Revisar datos">
                    <Pencil size={15} />
                  </Link>
                </div>
              ) : null}
            </div>
            {interesadosVisibles.length === 0 ? <p className="rail-muted">No hay interesados registrados.</p> : null}
            <div className="request-people-compact">
              {interesadosVisibles.map((interesado, index) => {
                const direccion = formatInteresadoAddress(interesado);
                return (
                  <article className="request-person-card" key={`${interesado.dni}-${index}`}>
                    <header>
                      <div>
                        <strong>{interesado.nombre || "Interesado"}</strong>
                        <span>{interesado.rol ? formatEnum(interesado.rol) : "Sin rol asignado"}</span>
                      </div>
                      <div className="request-person-card__badges">
                        <StatusBadge tone={interesado.clienteHabitual ? "info" : "neutral"}>
                          {interesado.clienteHabitual ? "Habitual" : "Puntual"}
                        </StatusBadge>
                        <StatusBadge tone={interesado.documentoIdentidadAportado ? "success" : "warning"}>
                          {interesado.documentoIdentidadAportado ? "DNI OK" : "Falta DNI"}
                        </StatusBadge>
                      </div>
                    </header>
                    <dl className="request-person-facts">
                      <div>
                        <dt><IdCard size={13} /> ID</dt>
                        <dd>{interesado.dni || "No consta"}</dd>
                      </div>
                      <div>
                        <dt><Phone size={13} /> Tel.</dt>
                        <dd>{interesado.telefono || "No consta"}</dd>
                      </div>
                      <div className="request-person-facts__wide">
                        <dt><MapPin size={13} /> Direccion</dt>
                        <dd>{direccion || "No consta"}</dd>
                      </div>
                    </dl>
                    {interesado.requiereRepresentanteLegal ? (
                      <div className={interesado.representanteLegalAportado ? "request-person-note is-success" : "request-person-note is-warning"}>
                        {interesado.representanteLegalAportado ? <CheckCircle2 size={14} /> : <AlertTriangle size={14} />}
                        <span>
                          Representante legal: {interesado.representanteLegalNombre || "pendiente"}
                          {interesado.representanteLegalDni ? ` (${interesado.representanteLegalDni})` : ""}
                        </span>
                      </div>
                    ) : null}
                  </article>
                );
              })}
            </div>
          </section>
        </div>
      </div>

      {isAdmin ? (
        <AdminActions
          solicitud={solicitud}
          isClosed={isClosed}
          onConvert={handleConvertSolicitud}
          onOpenTemplates={() => setTemplateDialogOpen(true)}
          onReadWithIa={() => handleProcessDocumentacionIa(false)}
          onForceReadWithIa={() => handleProcessDocumentacionIa(true)}
          onResetIaData={() => void handleResetDatosIa()}
          onStateChange={(estado) => estadoMutation.mutate({ solicitudId: solicitud.id, estado })}
          canReadWithIa={hasSolicitudDocuments}
          iaPending={procesarDocumentacionMutation.isPending}
          pending={checkingInteresados || convertirMutation.isPending || estadoMutation.isPending || procesarDocumentacionMutation.isPending || resetDatosIaMutation.isPending}
        />
      ) : null}

      {iaResult ? <SolicitudIaResultPanel response={iaResult} onDismiss={() => setIaResult(null)} /> : null}
      {iaError ? <SolicitudIaErrorPanel message={iaError} onDismiss={() => setIaError(null)} /> : null}

      {!isClosed ? (
        <>
          <SolicitudPreparationAssistant
            editPath={editSolicitudPath}
            error={preparacionQuery.isError}
            iaDisabled={preparationIaDisabled}
            iaLabel={preparationIaLabel}
            iaPending={preparationIaPending}
            loading={preparacionQuery.isLoading}
            onReadWithIa={isAdmin ? () => handleProcessDocumentacionIa(false) : () => void handleProcessClienteIa()}
            onOpenTemplates={() => setTemplateDialogOpen(true)}
            preparation={preparacionQuery.data}
            showIaShortcut={!isAdmin}
            title={preparationTitle}
          />
          <div className="request-upload-compact" id="solicitud-documentacion-completa">
            <CompleteExpedienteUploadPanel
              onUploadCompleteExpediente={handleUploadCompleteSolicitud}
              processing={completeSolicitudProcessing}
              processingJob={completeSolicitudJob}
              minimized={completeSolicitudMinimized}
              onToggleMinimized={() => setCompleteSolicitudMinimized((current) => !current)}
              title="Aportar documentacion completa"
              description="Sube el PDF completo para separar automaticamente los documentos."
            />
          </div>
        </>
      ) : null}

      <div className="request-grid request-grid--wide">
        <section className="panel" id="solicitud-documentos">
          <div className="panel-heading">
            <h2>Documentos</h2>
            <div className="button-group">
              <button
                className="soft-button soft-button--compact"
                disabled={!hasSolicitudDocuments}
                onClick={handleOpenDocumentReview}
                type="button"
              >
                <Scissors size={16} />
                Revisar documentos
              </button>
            </div>
          </div>
          <div className="document-table">
            {solicitud.documentos.length === 0 ? <div className="document-table__empty">No hay documentos asociados.</div> : null}
            {solicitud.documentos.map((documento) => (
              <div className="document-table__row" key={documento.id}>
                <FileText size={20} />
                <div className="document-table__main">
                  <strong>{documento.nombreOriginal || documento.nombre}</strong>
                  <span>{formatDocumentType(documento.tipo)}{documento.interesadoNombre ? ` - ${documento.interesadoNombre}` : ""}</span>
                  <SolicitudDocumentReading
                    documento={documento}
                    canAddIdentity={!isClosed}
                    canRereadIdentity={isAdmin && !isClosed}
                    existingIdentities={existingSolicitudIdentities}
                    addingIdentity={anadirIdentidadDetectadaMutation.isPending}
                    rereadingIdentity={releerIdentidadMutation.isPending}
                    onAddIdentity={handleAddDetectedIdentity}
                    onRereadIdentity={handleRereadIdentity}
                  />
                </div>
                <small>{documento.fechaSubida || "Sin fecha"}</small>
                <a className="soft-button soft-button--compact" href={`/documentos/ver/${documento.id}`} target="_blank" rel="noreferrer">
                  Ver
                </a>
              </div>
            ))}
          </div>
        </section>

        <section className="panel panel--messages">
          <h2>
            <MessageSquare size={18} /> Mensajes
          </h2>
          <div className="message-list">
            {solicitud.mensajes.length === 0 ? <p className="rail-muted">Aun no hay mensajes en esta solicitud.</p> : null}
            {solicitud.mensajes.map((item) => (
              <article className={item.rolAutor === "ADMIN" ? "message message--admin" : "message message--cliente"} key={item.id}>
                <div>
                  <strong>{item.autor}</strong>
                  <span>{item.fechaCreacion}</span>
                </div>
                <p>{item.contenido}</p>
              </article>
            ))}
          </div>
          <form
            className="message-form"
            onSubmit={(event) => {
              event.preventDefault();
              if (mensaje.trim()) {
                mensajeMutation.mutate({ solicitudId: solicitud.id, contenido: mensaje.trim() });
              }
            }}
          >
            <textarea value={mensaje} onChange={(event) => setMensaje(uppercaseInput(event.target.value))} placeholder="Escribe un mensaje..." rows={3} />
            <button className="primary-button primary-button--compact" disabled={mensajeMutation.isPending || !mensaje.trim()}>
              <Send size={16} />
              Enviar
            </button>
          </form>
        </section>
      </div>

      {solicitud.incidencias.length > 0 ? (
        <section className="panel">
          <h2>Incidencias</h2>
          <div className="incident-list">
            {solicitud.incidencias.map((incidencia) => (
              <div className="incident-row" key={incidencia.id}>
                {incidencia.resuelta ? <CheckCircle2 size={20} /> : <AlertTriangle size={20} />}
                <div>
                  <strong>{incidencia.tipo || "Incidencia"}</strong>
                  <p>{incidencia.observaciones}</p>
                  <small>{incidencia.fechaCreacion}</small>
                </div>
                <StatusBadge tone={incidencia.resuelta ? "success" : "danger"}>{incidencia.resuelta ? "Resuelta" : "Abierta"}</StatusBadge>
              </div>
            ))}
          </div>
        </section>
      ) : null}

      {habitualModalOpen && !isClosed ? (
        <div className="exp-modal" role="presentation">
          <button className="exp-modal__backdrop" onClick={() => setHabitualModalOpen(false)} type="button" aria-label="Cerrar clientes habituales" />
          <section aria-labelledby="habituales-solicitud-title" aria-modal="true" className="exp-modal__panel exp-modal__panel--wide request-modal-panel" role="dialog">
            <div className="exp-modal__header">
              <div>
                <p className="eyebrow">Asignacion rapida</p>
                <h3 id="habituales-solicitud-title">Clientes habituales</h3>
              </div>
              <button className="icon-button" onClick={() => setHabitualModalOpen(false)} type="button" aria-label="Cerrar">
                <X size={16} />
              </button>
            </div>
            <SolicitudHabitualesPanel
              adding={asignarHabitualMutation.isPending}
              existingIdentifiers={existingIdentityIdentifiers}
              habituales={habitualesQuery.data ?? []}
              loading={habitualesQuery.isLoading}
              onAssign={handleAssignHabitual}
              onSearch={setHabitualSearch}
              search={habitualSearch}
            />
          </section>
        </div>
      ) : null}

      <OcrReviewDialog
        documentos={ocrReviewDocuments}
        onDeleteDocument={handleDeleteDocument}
        onDeletePages={handleDeleteOcrPages}
        onExtractPages={handleExtractOcrPages}
        onMergeDocuments={handleMergeOcrDocuments}
        onSaveDocument={handleSaveOcrDocument}
        onClose={() => setOcrReviewOpen(false)}
        open={ocrReviewOpen}
      />
      <DocumentTemplateDialog
        solicitudId={solicitud.id}
        scope="solicitud"
        onClose={() => setTemplateDialogOpen(false)}
        open={templateDialogOpen}
      />
      {procesarDocumentacionMutation.isPending ? <SolicitudIaProgressModal /> : null}
      {dialog}
    </section>
  );
}

function SolicitudHabitualesPanel({
  adding,
  existingIdentifiers,
  habituales,
  loading,
  onAssign,
  onSearch,
  search,
}: {
  adding: boolean;
  existingIdentifiers: Set<string>;
  habituales: SolicitudInteresadoHabitual[];
  loading: boolean;
  onAssign: (habitual: SolicitudInteresadoHabitual, rol: string) => void;
  onSearch: (value: string) => void;
  search: string;
}) {
  const [selectedRoles, setSelectedRoles] = useState<Record<number, string>>({});
  return (
    <div className="request-habituals">
      <div className="request-habituals__heading">
        <div>
          <strong>Clientes habituales</strong>
          <span>{habituales.length > 0 ? `${habituales.length} disponibles` : "Sin resultados"}</span>
        </div>
        <input
          aria-label="Buscar cliente habitual"
          onChange={(event) => onSearch(uppercaseInput(event.target.value))}
          placeholder="Buscar por nombre o DNI"
          value={search}
        />
      </div>
      <div className="request-habituals__list">
        {loading ? <p className="rail-muted">Cargando clientes habituales...</p> : null}
        {!loading && habituales.length === 0 ? <p className="rail-muted">No hay clientes habituales que coincidan.</p> : null}
        {habituales.map((habitual) => {
          const normalizedId = normalizeIdentityIdentifier(habitual.dni);
          const alreadyAdded = Boolean(normalizedId && existingIdentifiers.has(normalizedId));
          const selectedRole = selectedRoles[habitual.id] || "";
          const direccion = formatHabitualAddress(habitual);
          return (
            <article className="request-habitual-card" key={habitual.id}>
              <div className="request-habitual-card__main">
                <span className="row-icon" aria-hidden="true">
                  <UserRound size={16} />
                </span>
                <div>
                  <strong>{habitual.nombre || "Cliente habitual"}</strong>
                  <small>{[habitual.dni, direccion].filter(Boolean).join(" · ") || "Sin datos adicionales"}</small>
                </div>
              </div>
              <div className="request-habitual-card__meta">
                <StatusBadge tone={habitual.documentoIdentidadAportado ? "success" : "warning"}>
                  {habitual.documentoIdentidadAportado ? "DNI/CIF guardado" : "Sin DNI/CIF guardado"}
                </StatusBadge>
                <small>{habitual.documentos} doc.</small>
              </div>
              <div className="request-habitual-card__actions">
                <select
                  aria-label="Rol del cliente habitual"
                  disabled={adding || alreadyAdded}
                  onChange={(event) => setSelectedRoles((current) => ({ ...current, [habitual.id]: event.target.value }))}
                  value={selectedRole}
                >
                  <option value="">Rol</option>
                  {SOLICITUD_ROLES.map((rol) => (
                    <option key={rol} value={rol}>{formatEnum(rol)}</option>
                  ))}
                </select>
                <button
                  className="soft-button soft-button--compact"
                  disabled={adding || alreadyAdded || !selectedRole || !normalizedId}
                  onClick={() => onAssign(habitual, selectedRole)}
                  type="button"
                >
                  {adding ? <Loader2 size={14} /> : <UserPlus size={14} />}
                  {alreadyAdded ? "Incluido" : "Asignar"}
                </button>
              </div>
            </article>
          );
        })}
      </div>
    </div>
  );
}

type SolicitudExistingIdentity = {
  identificador: string;
  rol?: string | null;
  nombre?: string | null;
};

function SolicitudDocumentReading({
  documento,
  canAddIdentity = false,
  canRereadIdentity = false,
  existingIdentities,
  addingIdentity,
  rereadingIdentity,
  onAddIdentity,
  onRereadIdentity,
}: {
  documento: DocumentoExpediente;
  canAddIdentity?: boolean;
  canRereadIdentity?: boolean;
  existingIdentities: SolicitudExistingIdentity[];
  addingIdentity: boolean;
  rereadingIdentity: boolean;
  onAddIdentity: (documento: DocumentoExpediente, identidad: DocumentoIdentidadDetectada, rol: string, identificador: string) => void;
  onRereadIdentity: (documento: DocumentoExpediente) => void;
}) {
  const [selectedRoles, setSelectedRoles] = useState<Record<string, string>>({});
  const [identifiers, setIdentifiers] = useState<Record<string, string>>({});
  const identidad = documento.lecturaIdentidad;
  const roles = documento.lecturaRoles;
  const vehiculo = documento.lecturaVehiculo;
  if (!identidad && !roles && !vehiculo) {
    return null;
  }
  if (identidad) {
    const detectadas = compactIdentityOptions(
      identidad.identidadesDetectadas?.length ? identidad.identidadesDetectadas : [lecturaPrincipalComoIdentidad(identidad)],
    );
    return (
      <div className={identidad.requiereRevision ? "solicitud-reading is-warning" : "solicitud-reading is-success"}>
        <div className="solicitud-reading__header">
          <strong>{detectadas.length > 1 ? `${detectadas.length} DNI/CIF detectados` : "DNI/CIF detectado"}</strong>
          <div className="solicitud-reading__header-actions">
            <span>{confidenceLabel(identidad.confianzaGlobal)}</span>
            {canRereadIdentity ? (
              <button
                className="soft-button soft-button--compact"
                disabled={rereadingIdentity}
                onClick={() => onRereadIdentity(documento)}
                type="button"
              >
                {rereadingIdentity ? <Loader2 size={14} /> : <RefreshCw size={14} />}
                Releer
              </button>
            ) : null}
          </div>
        </div>
        <div className="solicitud-reading-identities">
          {detectadas.map((item, index) => {
            const key = identityKey(item, index);
            const normalizedId = normalizeIdentityIdentifier(item.identificador);
            const identifierValue = identifiers[key] ?? normalizedId ?? "";
            const normalizedEditedId = normalizeIdentityIdentifier(identifierValue);
            const matchedExisting = normalizedEditedId
              ? existingIdentities.find((existing) => existing.identificador === normalizedEditedId)
              : null;
            const selectedRole = matchedExisting?.rol || selectedRoles[key] || "";
            const roleLocked = Boolean(matchedExisting?.rol);
            const submitLabel = matchedExisting ? "Asignar" : "Anadir";
            return (
              <div className="solicitud-reading-identity" key={key}>
                <div className="solicitud-reading-identity__main">
                  <div className="solicitud-reading-identity__summary">
                    <div>
                      <span>{item.tipoDocumentoDetectado || "Identidad"}</span>
                      <strong>{identityDisplayName(item) || "Nombre no detectado"}</strong>
                    </div>
                    <code>{normalizedId || "Sin DNI/CIF"}</code>
                  </div>
                  <div className="solicitud-reading-identity__meta">
                    {item.fechaNacimiento ? <span>Nac. {item.fechaNacimiento}</span> : null}
                    {item.fechaCaducidad ? <span>Cad. {item.fechaCaducidad}</span> : null}
                    {item.direccionTexto ? <span>{item.direccionTexto}</span> : null}
                  </div>
                  {matchedExisting ? (
                    <p className="solicitud-reading-identity__match">Coincide con interesado: {[matchedExisting.nombre, matchedExisting.rol ? formatEnum(matchedExisting.rol) : null].filter(Boolean).join(" - ")}</p>
                  ) : null}
                  {item.observaciones ? (
                    <p className="solicitud-reading-identity__note">{item.observaciones}</p>
                  ) : null}
                </div>
                {canAddIdentity ? (
                  <div className="solicitud-reading-identity__actions">
                    <input
                      aria-label="DNI/NIE/CIF revisado"
                      className="solicitud-reading-identity__identifier"
                      disabled={addingIdentity}
                      onChange={(event) => setIdentifiers((current) => ({ ...current, [key]: uppercaseInput(event.target.value) }))}
                      placeholder="DNI/NIE/CIF"
                      value={identifierValue}
                    />
                    <select
                      aria-label="Rol del interesado"
                      disabled={addingIdentity || roleLocked}
                      onChange={(event) => setSelectedRoles((current) => ({ ...current, [key]: event.target.value }))}
                      value={selectedRole}
                    >
                      <option value="">Rol</option>
                      {SOLICITUD_ROLES.map((rol) => (
                        <option key={rol} value={rol}>{formatEnum(rol)}</option>
                      ))}
                    </select>
                    <button
                      className="soft-button soft-button--compact"
                      disabled={addingIdentity || !selectedRole || !normalizedEditedId}
                      onClick={() => onAddIdentity(documento, item, selectedRole, identifierValue)}
                      type="button"
                    >
                      {addingIdentity ? <Loader2 size={14} /> : <UserPlus size={14} />}
                      {submitLabel}
                    </button>
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>
        <em>{identidad.mensaje || (identidad.requiereRevision ? "Revisar lectura" : "Lectura valida")}</em>
      </div>
    );
  }
  if (roles) {
    return (
      <div className={roles.requiereRevision ? "solicitud-reading is-warning" : "solicitud-reading is-success"}>
        <strong>Roles detectados</strong>
        <span>Vendedor: {[roles.vendedorIdentificador, roles.vendedorNombre].filter(Boolean).join(" - ") || "Sin dato"}</span>
        <span>Comprador: {[roles.compradorIdentificador, roles.compradorNombre].filter(Boolean).join(" - ") || "Sin dato"}</span>
        {[roles.matricula, roles.bastidor].filter(Boolean).length ? <small>{[roles.matricula, roles.bastidor].filter(Boolean).join(" / ")}</small> : null}
        <em>{confidenceLabel(roles.confianzaGlobal)} / {roles.mensaje || roles.motivoAplicacion || "Lectura registrada"}</em>
      </div>
    );
  }
  return vehiculo ? (
    <div className={vehiculo.requiereRevision ? "solicitud-reading is-warning" : "solicitud-reading is-success"}>
      <strong>Vehiculo detectado</strong>
      <span>{[vehiculo.marca, vehiculo.modeloVehiculo].filter(Boolean).join(" ") || "Sin marca/modelo"}</span>
      {[vehiculo.matricula, vehiculo.bastidor].filter(Boolean).length ? <small>{[vehiculo.matricula, vehiculo.bastidor].filter(Boolean).join(" / ")}</small> : null}
      <em>{confidenceLabel(vehiculo.confianzaGlobal)} / {vehiculo.mensaje || "Lectura registrada"}</em>
    </div>
  ) : null;
}

function confidenceLabel(value?: number | null) {
  return typeof value === "number" ? `${Math.round(value * 100)}% confianza` : "Sin confianza";
}

function lecturaPrincipalComoIdentidad(lectura: DocumentoIdentidadLectura): DocumentoIdentidadDetectada {
  return {
    tipoDocumentoDetectado: lectura.tipoDocumentoDetectado,
    identificador: lectura.identificador,
    nombre: lectura.nombre,
    apellido1: lectura.apellido1,
    apellido2: lectura.apellido2,
    razonSocial: lectura.razonSocial,
    nombreCompleto: lectura.nombreCompleto,
    fechaNacimiento: lectura.fechaNacimiento,
    fechaCaducidad: lectura.fechaCaducidad,
    direccionTexto: lectura.direccionTexto,
    confianzaGlobal: lectura.confianzaGlobal,
    requiereRevision: lectura.requiereRevision,
    observaciones: lectura.mensaje,
  };
}

function compactIdentityOptions(identidades: DocumentoIdentidadDetectada[]) {
  const result: DocumentoIdentidadDetectada[] = [];
  identidades.forEach((item) => {
    const normalizedId = normalizeIdentityIdentifier(item.identificador);
    const normalizedItem = {
      ...item,
      identificador: normalizedId?.startsWith("IDESP") ? null : normalizedId || item.identificador,
    };
    if (!normalizedItem.identificador && !identityDisplayName(normalizedItem)) {
      return;
    }
    const index = result.findIndex((existing) => sameIdentityOption(existing, normalizedItem));
    if (index >= 0) {
      result[index] = mergeIdentityOption(result[index], normalizedItem);
    } else {
      result.push(normalizedItem);
    }
  });
  return result;
}

function sameIdentityOption(first: DocumentoIdentidadDetectada, second: DocumentoIdentidadDetectada) {
  const firstId = normalizeIdentityIdentifier(first.identificador);
  const secondId = normalizeIdentityIdentifier(second.identificador);
  if (firstId && secondId && firstId === secondId) {
    return true;
  }
  const firstName = normalizeIdentityName(first);
  const secondName = normalizeIdentityName(second);
  return Boolean(firstName && firstName === secondName && (!firstId || !secondId));
}

function mergeIdentityOption(first: DocumentoIdentidadDetectada, second: DocumentoIdentidadDetectada): DocumentoIdentidadDetectada {
  const firstId = normalizeIdentityIdentifier(first.identificador);
  const secondId = normalizeIdentityIdentifier(second.identificador);
  return {
    ...first,
    ...second,
    identificador: firstId || secondId || first.identificador || second.identificador,
    nombre: second.nombre || first.nombre,
    apellido1: second.apellido1 || first.apellido1,
    apellido2: second.apellido2 || first.apellido2,
    razonSocial: second.razonSocial || first.razonSocial,
    nombreCompleto: second.nombreCompleto || first.nombreCompleto,
    fechaNacimiento: second.fechaNacimiento || first.fechaNacimiento,
    fechaCaducidad: second.fechaCaducidad || first.fechaCaducidad,
    direccionTexto: second.direccionTexto || first.direccionTexto,
    confianzaGlobal: Math.max(first.confianzaGlobal ?? 0, second.confianzaGlobal ?? 0) || first.confianzaGlobal || second.confianzaGlobal,
    requiereRevision: first.requiereRevision && second.requiereRevision,
    observaciones: second.observaciones || first.observaciones,
  };
}

function identityKey(item: DocumentoIdentidadDetectada, index: number) {
  return normalizeIdentityIdentifier(item.identificador) || `${normalizeIdentityName(item) || "identidad"}-${index}`;
}

function identityDisplayName(item: DocumentoIdentidadDetectada) {
  return item.nombreCompleto
    || item.razonSocial
    || [item.nombre, item.apellido1, item.apellido2].filter(Boolean).join(" ").trim()
    || null;
}

function normalizeIdentityName(item: DocumentoIdentidadDetectada) {
  return identityDisplayName(item)?.toUpperCase().replace(/[^A-Z0-9]/g, "") || null;
}

function normalizeIdentityIdentifier(value?: string | null) {
  const normalized = value?.toUpperCase().replace(/[^A-Z0-9]/g, "") || "";
  return normalized || null;
}

function SolicitudPreparationAssistant({
  preparation,
  loading,
  error,
  title,
  editPath,
  iaDisabled,
  iaLabel,
  iaPending,
  onReadWithIa,
  onOpenTemplates,
  showIaShortcut = false,
}: {
  preparation?: SolicitudPreparacionTraspaso;
  loading: boolean;
  error: boolean;
  title: string;
  editPath: string;
  iaDisabled: boolean;
  iaLabel: string;
  iaPending: boolean;
  onReadWithIa: () => void;
  onOpenTemplates: () => void;
  showIaShortcut?: boolean;
}) {
  const [detailOpen, setDetailOpen] = useState(false);

  if (loading) {
    return (
      <section className="request-assistant request-assistant--loading" aria-label={title}>
        <div className="request-assistant__top">
          <div className="request-assistant__title">
            <span className="request-assistant__icon is-info">
              <Loader2 size={18} />
            </span>
            <div>
              <p className="eyebrow">{title}</p>
              <h3>Calculando estado</h3>
            </div>
          </div>
        </div>
        <div className="request-assistant__bar">
          <span style={{ width: "34%" }} />
        </div>
      </section>
    );
  }

  if (error || !preparation) {
    return (
      <section className="request-assistant request-assistant--warning" aria-label={title}>
        <div className="request-assistant__top">
          <div className="request-assistant__title">
            <span className="request-assistant__icon is-warning">
              <AlertTriangle size={18} />
            </span>
            <div>
              <p className="eyebrow">{title}</p>
              <h3>No se pudo calcular la preparacion</h3>
            </div>
          </div>
        </div>
      </section>
    );
  }

  const progress = clampPercent(preparation.progreso);
  const tone = preparationTone(preparation.estado);
  const action = preparation.siguienteAccion;
  const showSecondaryIaAction = showIaShortcut && action?.tipo !== "REVISAR_IA";

  return (
    <section className={`request-assistant request-assistant--${tone}`} aria-label={title}>
      <div className="request-assistant__top">
        <div className="request-assistant__title">
          <span className={`request-assistant__icon is-${tone}`}>{preparationIcon(preparation.estado)}</span>
          <div>
            <p className="eyebrow">{title}</p>
            <h3>{preparationHeadline(preparation)}</h3>
          </div>
        </div>
        <div className="request-assistant__summary">
          <strong>{progress}%</strong>
          <span>{preparationLabel(preparation.estado)}</span>
        </div>
      </div>
      <div className="request-assistant__bar" aria-hidden="true">
        <span style={{ width: `${progress}%` }} />
      </div>
      {action ? (
        <div className="request-assistant__action">
          <div>
            <strong>{action.titulo}</strong>
            {action.detalle ? <span>{action.detalle}</span> : null}
          </div>
          <SolicitudPreparationAction
            iaDisabled={iaDisabled}
            iaLabel={iaLabel}
            iaPending={iaPending}
            onReadWithIa={onReadWithIa}
            tipo={action.tipo}
            editPath={editPath}
            onOpenTemplates={onOpenTemplates}
          />
        </div>
      ) : null}
      <div className="request-assistant__tools">
        {showSecondaryIaAction ? (
          <button className="soft-button soft-button--compact" disabled={iaDisabled} onClick={onReadWithIa} type="button">
            {iaPending ? <Loader2 size={16} /> : <Sparkles size={16} />}
            {iaLabel}
          </button>
        ) : null}
        <button className="soft-button soft-button--compact" onClick={() => setDetailOpen(true)} type="button">
          <Info size={16} />
          Ver detalle
        </button>
      </div>
      {detailOpen ? (
        <div className="exp-modal" role="presentation">
          <button className="exp-modal__backdrop" onClick={() => setDetailOpen(false)} type="button" aria-label="Cerrar detalle del asistente" />
          <section aria-labelledby="preparation-detail-title" aria-modal="true" className="exp-modal__panel exp-modal__panel--wide request-modal-panel request-assistant-modal" role="dialog">
            <div className="exp-modal__header">
              <div>
                <p className="eyebrow">{title}</p>
                <h3 id="preparation-detail-title">Detalle de preparacion</h3>
              </div>
              <button className="icon-button" onClick={() => setDetailOpen(false)} type="button" aria-label="Cerrar">
                <X size={16} />
              </button>
            </div>
            <div className="request-assistant-modal__body">
              <div className="request-assistant__blocks">
                {preparation.bloques.map((bloque) => (
                  <SolicitudPreparationBlock bloque={bloque} key={bloque.codigo} />
                ))}
              </div>
              {preparation.documentosGenerables.length > 0 ? (
                <div className="request-assistant__documents">
                  <div className="request-assistant__section-title">
                    <FileSignature size={16} />
                    <strong>Documentos</strong>
                  </div>
                  <ul>
                    {preparation.documentosGenerables.map((documento) => (
                      <SolicitudPreparationDocument documento={documento} key={documento.codigo} />
                    ))}
                  </ul>
                </div>
              ) : null}
            </div>
          </section>
        </div>
      ) : null}
    </section>
  );
}

function SolicitudPreparationBlock({ bloque }: { bloque: SolicitudPreparacionBloque }) {
  const tone = preparationTone(bloque.estado);
  const pendingItem = bloque.items.find((item) => item.estado !== "OK") ?? bloque.items[0];
  const progress = bloque.total > 0 ? clampPercent((bloque.completados / bloque.total) * 100) : 0;
  return (
    <div className={`request-assistant__block is-${tone}`}>
      <div className="request-assistant__block-head">
        <span>{preparationIcon(bloque.estado)}</span>
        <div>
          <strong>{bloque.titulo}</strong>
          <small>{pendingItem?.detalle || (bloque.estado === "OK" ? "Completo" : "Pendiente de revisar")}</small>
          {pendingItem?.accionLabel ? <em>{pendingItem.accionLabel}</em> : null}
        </div>
        <b>{bloque.completados}/{bloque.total}</b>
      </div>
      <div className="request-assistant__block-progress" aria-hidden="true">
        <span style={{ width: `${progress}%` }} />
      </div>
    </div>
  );
}

function SolicitudPreparationDocument({ documento }: { documento: SolicitudPreparacionDocumento }) {
  const tone = documentTone(documento.estado);
  return (
    <li className={`is-${tone}`}>
      <span>
        <strong>{documento.nombre}</strong>
        {documento.faltantes.length > 0 ? <small>Falta: {formatReadableList(documento.faltantes)}.</small> : null}
      </span>
      <em>{documentLabel(documento.estado, documento.camposCompletos, documento.camposTotales)}</em>
    </li>
  );
}

function SolicitudPreparationAction({
  tipo,
  editPath,
  iaDisabled,
  iaLabel,
  iaPending,
  onReadWithIa,
  onOpenTemplates,
}: {
  tipo?: string | null;
  editPath: string;
  iaDisabled: boolean;
  iaLabel: string;
  iaPending: boolean;
  onReadWithIa: () => void;
  onOpenTemplates: () => void;
}) {
  if (!tipo || tipo === "NINGUNA") return null;
  if (tipo === "GENERAR_DOCUMENTOS" || tipo === "COMPLETAR_PLANTILLA") {
    return (
      <button className="primary-button primary-button--compact" onClick={onOpenTemplates} type="button">
        <FileSignature size={16} />
        Generar docs
      </button>
    );
  }
  if (tipo === "SUBIR_DOCUMENTO") {
    return (
      <a className="soft-button soft-button--compact" href="#solicitud-documentacion-completa">
        <FileText size={16} />
        Aportar docs
      </a>
    );
  }
  if (tipo === "REVISAR_IA") {
    return (
      <button className="primary-button primary-button--compact" disabled={iaDisabled} onClick={onReadWithIa} type="button">
        {iaPending ? <Loader2 size={16} /> : <Sparkles size={16} />}
        {iaLabel}
      </button>
    );
  }
  if (tipo.startsWith("COMPLETAR") || tipo.startsWith("REVISAR")) {
    return (
      <Link className="soft-button soft-button--compact" to={editPath}>
        <Pencil size={16} />
        Editar datos
      </Link>
    );
  }
  return null;
}

function clampPercent(value?: number | null) {
  return Math.max(0, Math.min(100, Math.round(value ?? 0)));
}

function preparationTone(estado?: string | null) {
  if (estado === "LISTA" || estado === "OK") return "success";
  if (estado === "BLOQUEADA" || estado === "BLOQUEANTE") return "danger";
  if (estado === "AVISO") return "warning";
  return "info";
}

function preparationIcon(estado?: string | null) {
  const tone = preparationTone(estado);
  if (tone === "success") return <CheckCircle2 size={16} />;
  if (tone === "danger" || tone === "warning") return <AlertTriangle size={16} />;
  return <Info size={16} />;
}

function preparationLabel(estado?: string | null) {
  if (estado === "LISTA") return "Lista";
  if (estado === "BLOQUEADA") return "Bloqueada";
  if (estado === "INCOMPLETA") return "Incompleta";
  return formatEnum(estado);
}

function preparationHeadline(preparation: SolicitudPreparacionTraspaso) {
  if (preparation.estado === "LISTA") return "Lista para generar documentos";
  if (preparation.estado === "BLOQUEADA") return "Faltan datos obligatorios";
  return preparation.siguienteAccion?.titulo || "Preparacion en curso";
}

function documentTone(estado?: string | null) {
  if (estado === "YA_APORTADO" || estado === "LISTO") return "success";
  if (estado === "FALTAN_DATOS") return "warning";
  return "info";
}

function documentLabel(estado?: string | null, completos?: number, total?: number) {
  if (estado === "YA_APORTADO") return "Aportado";
  if (estado === "LISTO") return "Listo";
  if (estado === "FALTAN_DATOS") return `${completos}/${total}`;
  return formatEnum(estado);
}

function SolicitudIaErrorPanel({ message, onDismiss }: { message: string; onDismiss: () => void }) {
  return (
    <section className="solicitud-ia-result solicitud-ia-result--danger" role="alert" aria-live="assertive">
      <div className="solicitud-ia-result__heading">
        <AlertTriangle size={20} />
        <div>
          <strong>No se pudo actualizar con IA</strong>
          <span>{message}</span>
        </div>
        <button className="soft-button soft-button--compact" type="button" onClick={onDismiss}>Cerrar</button>
      </div>
    </section>
  );
}

function SolicitudIaResultPanel({ response, onDismiss }: { response: SolicitudDocumentacionIaResponse; onDismiss: () => void }) {
  const tone = response.requiereRevision ? "warning" : response.datosAplicados || response.yaEstabaCorrecta ? "success" : "info";
  const title = response.requiereRevision
    ? "Lectura completada con revision pendiente"
    : response.yaEstabaCorrecta
      ? "La solicitud ya estaba correcta"
      : response.datosAplicados
        ? "Datos aplicados a la solicitud"
        : "Lectura completada";
  return (
    <section className={`solicitud-ia-result solicitud-ia-result--${tone}`} role="status" aria-live="polite">
      <div className="solicitud-ia-result__heading">
        {tone === "warning" ? <AlertTriangle size={20} /> : tone === "success" ? <CheckCircle2 size={20} /> : <Info size={20} />}
        <div>
          <strong>{title}</strong>
          <span>{response.mensaje || "Proceso finalizado."}</span>
        </div>
        <button className="soft-button soft-button--compact" type="button" onClick={onDismiss}>Cerrar</button>
      </div>
      <div className="solicitud-ia-result__metrics">
        <span>Identidades: {response.lecturasIdentidadNuevas} nuevas / {response.lecturasIdentidadReutilizadas} reutilizadas</span>
        <span>Roles: {response.lecturasRolesNuevas} nuevas / {response.lecturasRolesReutilizadas} reutilizadas</span>
      </div>
      {response.detalles?.length ? (
        <ul>
          {response.detalles.slice(0, 5).map((detalle) => <li key={detalle}>{detalle}</li>)}
        </ul>
      ) : null}
    </section>
  );
}

function clienteIaButtonText(lecturaIa?: LecturaIaSolicitudCliente | null, loading = false) {
  if (loading) return "Solicitando";
  if (!lecturaIa) return "No disponible";
  if (!lecturaIa.apiKeyConfigurada) return "IA no configurada";
  if (!lecturaIa.documentacionSuficiente) return "Documentacion pendiente";
  return "Solicitar lectura IA";
}

function AdminActions({
  solicitud,
  isClosed,
  pending,
  iaPending,
  canReadWithIa,
  onConvert,
  onOpenTemplates,
  onReadWithIa,
  onForceReadWithIa,
  onResetIaData,
  onStateChange,
}: {
  solicitud: SolicitudDetail;
  isClosed: boolean;
  pending: boolean;
  iaPending: boolean;
  canReadWithIa: boolean;
  onConvert: () => void;
  onOpenTemplates: () => void;
  onReadWithIa: () => void;
  onForceReadWithIa: () => void;
  onResetIaData: () => void;
  onStateChange: (estado: string) => void;
}) {
  return (
    <section className="request-admin-toolbar" aria-label="Acciones administrativas">
      <strong>Acciones</strong>
      {isClosed ? <span>Solicitud cerrada</span> : null}
      <div className="button-group">
        {!isClosed ? (
          <>
            <button className="primary-button primary-button--compact" disabled={pending || !canReadWithIa} onClick={onReadWithIa}>
              {iaPending ? <Loader2 size={16} /> : <FileText size={16} />}
              {iaPending ? "Leyendo IA" : "Lectura IA"}
            </button>
            <button className="soft-button soft-button--compact" disabled={pending || !canReadWithIa} onClick={onForceReadWithIa}>
              {iaPending ? <Loader2 size={16} /> : <RefreshCw size={16} />}
              Releer IA
            </button>
            <button className="soft-button soft-button--compact soft-button--danger" disabled={pending} onClick={onResetIaData} type="button">
              <RotateCcw size={16} />
              Reset datos IA
            </button>
            <button className="soft-button soft-button--compact" disabled={pending} onClick={onOpenTemplates} type="button">
              <FileSignature size={16} />
              Generar docs
            </button>
            <Link className="soft-button soft-button--compact" to={`/solicitudes/${solicitud.id}/editar`}>
              <Pencil size={16} />
              Editar datos
            </Link>
            <button className="primary-button primary-button--compact" disabled={pending} onClick={onConvert}>
              <FolderCheck size={16} />
              Convertir
            </button>
            <button className="soft-button soft-button--compact" disabled={pending} onClick={() => onStateChange("PENDIENTE_DOCUMENTACION")}>
              Pedir documentacion
            </button>
            <button className="soft-button soft-button--compact" disabled={pending} onClick={() => onStateChange("RECHAZADO")}>
              Rechazar
            </button>
          </>
        ) : null}
        {solicitud.estado === "PENDIENTE_DOCUMENTACION" ? (
          <button className="soft-button soft-button--compact" disabled={pending} onClick={() => onStateChange("PENDIENTE_REVISION")}>
            Volver a revision
          </button>
        ) : null}
      </div>
    </section>
  );
}

function SolicitudIaProgressModal() {
  return (
    <div className="ga-progress-modal" role="status" aria-live="polite">
      <div className="ga-progress-modal__panel">
        <div className="ga-progress-modal__heading">
          <span className="ga-progress-modal__spinner">
            <Loader2 size={22} />
          </span>
          <div>
            <p className="eyebrow">Procesando documentacion</p>
            <h3>Leyendo DNI/CIF y factura</h3>
          </div>
          <strong>IA</strong>
        </div>
        <div className="ga-progress-modal__bar">
          <span style={{ width: "72%" }} />
        </div>
        <div className="ga-progress-modal__status">
          <strong>Actualizando solicitud</strong>
          <span>Reutilizando lecturas validas cuando ya existen</span>
        </div>
        <ul className="ga-progress-modal__steps">
          <li className="is-active">
            <span />
            Localizar identidades
          </li>
          <li className="is-active">
            <span />
            Leer factura o contrato
          </li>
          <li className="is-active">
            <span />
            Aplicar comprador y vendedor
          </li>
        </ul>
      </div>
    </div>
  );
}

function MiniPlate({ value }: { value?: string | null }) {
  if (!value) {
    return <span className="muted-text">Sin matricula</span>;
  }
  return (
    <span className="plate plate--large">
      <span>E</span>
      <strong>{value}</strong>
    </span>
  );
}

function statusTone(status?: string | null) {
  if (status === "CONVERTIDA") return "success";
  if (status === "RECHAZADO" || status === "PENDIENTE_DOCUMENTACION") return "danger";
  if (status === "REVISANDO_INCIDENCIAS") return "info";
  if (status === "PENDIENTE_REVISION") return "warning";
  return "neutral";
}

function formatEnum(value?: string | null) {
  return value ? value.replaceAll("_", " ") : "Sin estado";
}

function formatInteresadoAddress(interesado: InteresadoSolicitud) {
  const via = [
    interesado.tipoVia,
    interesado.nombreVia,
    interesado.numeroVia,
    withAddressLabel("BLOQ", interesado.bloque),
    withAddressLabel("PORTAL", interesado.portal),
    withAddressLabel("ESC", interesado.escalera),
    withAddressLabel("PISO", interesado.piso),
    withAddressLabel("PTA", interesado.puerta),
  ]
    .map((value) => value?.trim())
    .filter(Boolean)
    .join(" ");
  return [via || interesado.direccion, interesado.codigoPostal, interesado.municipio, interesado.provincia]
    .map((value) => value?.trim())
    .filter(Boolean)
    .join(", ");
}

function formatHabitualAddress(interesado: SolicitudInteresadoHabitual) {
  const via = [
    interesado.tipoVia,
    interesado.nombreVia,
    interesado.numeroVia,
    withAddressLabel("BLOQ", interesado.bloque),
    withAddressLabel("PORTAL", interesado.portal),
    withAddressLabel("ESC", interesado.escalera),
    withAddressLabel("PISO", interesado.piso),
    withAddressLabel("PTA", interesado.puerta),
  ]
    .map((value) => value?.trim())
    .filter(Boolean)
    .join(" ");
  return [via || interesado.direccion, interesado.codigoPostal, interesado.municipio, interesado.provincia]
    .map((value) => value?.trim())
    .filter(Boolean)
    .join(", ");
}

function hasInteresadoData(interesado: InteresadoSolicitud) {
  return [
    interesado.nombre,
    interesado.rol,
    interesado.dni,
    interesado.telefono,
    interesado.direccion,
    interesado.tipoVia,
    interesado.nombreVia,
    interesado.numeroVia,
    interesado.bloque,
    interesado.portal,
    interesado.escalera,
    interesado.piso,
    interesado.puerta,
    interesado.codigoPostal,
    interesado.municipio,
    interesado.provincia,
  ].some(
    (value) => value && value.trim() !== "",
  );
}

function withAddressLabel(label: string, value?: string | null) {
  const clean = value?.trim();
  return clean ? `${label} ${clean}` : "";
}

function formatReadableList(values: string[]) {
  const clean = values.map((value) => value.trim()).filter(Boolean);
  if (clean.length === 0) return "";
  if (clean.length === 1) return clean[0];
  if (clean.length === 2) return `${clean[0]} y ${clean[1]}`;
  return `${clean.slice(0, -1).join(", ")} y ${clean[clean.length - 1]}`;
}

function buildCoincidenciasDescription(coincidencias: Array<{
  rol?: string | null;
  dni?: string | null;
  camposDiferentes: string[];
  nombreRegistrado?: string | null;
  nombreDeclarado?: string | null;
  telefonoRegistrado?: string | null;
  telefonoDeclarado?: string | null;
  direccionRegistrada?: string | null;
  direccionDeclarada?: string | null;
}>) {
  return coincidencias
    .map((item) => {
      const diferencias = item.camposDiferentes.join(", ");
      const registrado = [item.nombreRegistrado, item.telefonoRegistrado, item.direccionRegistrada].filter(Boolean).join(" / ");
      const declarado = [item.nombreDeclarado, item.telefonoDeclarado, item.direccionDeclarada].filter(Boolean).join(" / ");
      return `${item.rol ? formatEnum(item.rol) + " - " : ""}${item.dni}: cambian ${diferencias}. Registro: ${registrado || "sin datos"}. Solicitud: ${declarado || "sin datos"}.`;
    })
    .join("\n");
}
