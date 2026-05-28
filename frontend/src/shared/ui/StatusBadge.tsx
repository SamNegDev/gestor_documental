import clsx from "clsx";
import type { ReactNode } from "react";

type StatusBadgeProps = {
  tone?: "neutral" | "warning" | "success" | "danger" | "info";
  children: ReactNode;
};

export function StatusBadge({ tone = "neutral", children }: StatusBadgeProps) {
  return <span className={clsx("status-badge", `status-badge--${tone}`)}>{children}</span>;
}
