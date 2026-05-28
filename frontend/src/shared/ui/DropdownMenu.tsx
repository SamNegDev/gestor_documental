import * as DropdownMenuPrimitive from "@radix-ui/react-dropdown-menu";
import { MoreHorizontal } from "lucide-react";
import type { ReactNode } from "react";

type ActionMenuProps = {
  children: ReactNode;
};

export function ActionMenu({ children }: ActionMenuProps) {
  return (
    <DropdownMenuPrimitive.Root>
      <DropdownMenuPrimitive.Trigger className="icon-button" aria-label="Abrir acciones">
        <MoreHorizontal size={18} />
      </DropdownMenuPrimitive.Trigger>
      <DropdownMenuPrimitive.Portal>
        <DropdownMenuPrimitive.Content className="dropdown-content" align="end" sideOffset={8}>
          {children}
        </DropdownMenuPrimitive.Content>
      </DropdownMenuPrimitive.Portal>
    </DropdownMenuPrimitive.Root>
  );
}

export const ActionMenuItem = DropdownMenuPrimitive.Item;
