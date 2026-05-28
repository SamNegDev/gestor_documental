import { NavLink } from "react-router-dom";
import type { LucideIcon } from "lucide-react";

type SidebarLinkProps = {
  to: string;
  icon: LucideIcon;
  label: string;
  external?: boolean;
};

export function SidebarLink({ to, icon: Icon, label, external = false }: SidebarLinkProps) {
  if (external) {
    return (
      <a href={to} className="sidebar-link">
        <Icon size={18} />
        <span>{label}</span>
      </a>
    );
  }

  return (
    <NavLink to={to} className={({ isActive }) => `sidebar-link ${isActive ? "is-active" : ""}`}>
      <Icon size={18} />
      <span>{label}</span>
    </NavLink>
  );
}
