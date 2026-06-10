import { AlertTriangle, CheckCircle2, Info, X } from "lucide-react";
import { useCallback, useState } from "react";

type ConfirmTone = "default" | "danger" | "success";

type ConfirmOptions = {
  title: string;
  description?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: ConfirmTone;
};

type ConfirmRequest = Required<Pick<ConfirmOptions, "title" | "confirmLabel" | "cancelLabel" | "tone">> &
  Pick<ConfirmOptions, "description"> & {
    resolve: (confirmed: boolean) => void;
  };

type ConfirmDialogProps = ConfirmOptions & {
  open: boolean;
  onCancel: () => void;
  onConfirm: () => void;
};

const iconByTone = {
  default: Info,
  danger: AlertTriangle,
  success: CheckCircle2,
};

export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = "Confirmar",
  cancelLabel = "Cancelar",
  tone = "default",
  onCancel,
  onConfirm,
}: ConfirmDialogProps) {
  if (!open) return null;

  const Icon = iconByTone[tone];

  return (
    <div className="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="confirm-dialog-title">
      <button className="confirm-dialog__backdrop" type="button" aria-label="Cancelar operacion" onClick={onCancel} />
      <section className={`confirm-dialog__panel confirm-dialog__panel--${tone}`}>
        <div className="confirm-dialog__icon" aria-hidden="true">
          <Icon size={22} />
        </div>
        <div className="confirm-dialog__content">
          <div className="confirm-dialog__heading">
            <h3 id="confirm-dialog-title">{title}</h3>
            <button className="icon-button" type="button" onClick={onCancel} title="Cerrar">
              <X size={16} />
            </button>
          </div>
          {description ? <p>{description}</p> : null}
          <div className="confirm-dialog__actions">
            <button className="soft-button" type="button" onClick={onCancel}>
              {cancelLabel}
            </button>
            <button className={`primary-button ${tone === "danger" ? "primary-button--danger" : ""}`} type="button" onClick={onConfirm}>
              {confirmLabel}
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}

export function useConfirmDialog() {
  const [request, setRequest] = useState<ConfirmRequest | null>(null);

  const close = useCallback(
    (confirmed: boolean) => {
      setRequest((current) => {
        current?.resolve(confirmed);
        return null;
      });
    },
    [],
  );

  const confirm = useCallback((options: ConfirmOptions) => {
    return new Promise<boolean>((resolve) => {
      setRequest({
        title: options.title,
        description: options.description,
        confirmLabel: options.confirmLabel || "Confirmar",
        cancelLabel: options.cancelLabel || "Cancelar",
        tone: options.tone || "default",
        resolve,
      });
    });
  }, []);

  const dialog = (
    <ConfirmDialog
      open={Boolean(request)}
      title={request?.title || ""}
      description={request?.description}
      confirmLabel={request?.confirmLabel}
      cancelLabel={request?.cancelLabel}
      tone={request?.tone}
      onCancel={() => close(false)}
      onConfirm={() => close(true)}
    />
  );

  return { confirm, dialog };
}
