import * as TooltipPrimitive from "@radix-ui/react-tooltip";
import type { ReactNode } from "react";

type TooltipProps = {
  label: string;
  children: ReactNode;
};

export function Tooltip({ label, children }: TooltipProps) {
  return (
    <TooltipPrimitive.Provider delayDuration={250}>
      <TooltipPrimitive.Root>
        <TooltipPrimitive.Trigger asChild>{children}</TooltipPrimitive.Trigger>
        <TooltipPrimitive.Portal>
          <TooltipPrimitive.Content className="tooltip-content" sideOffset={8}>
            {label}
            <TooltipPrimitive.Arrow className="tooltip-arrow" />
          </TooltipPrimitive.Content>
        </TooltipPrimitive.Portal>
      </TooltipPrimitive.Root>
    </TooltipPrimitive.Provider>
  );
}
