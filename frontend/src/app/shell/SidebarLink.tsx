import { NavLink } from "react-router-dom";
import type { LucideIcon } from "lucide-react";

type SidebarLinkProps = {
  to: string;
  icon: LucideIcon;
  label: string;
  external?: boolean;
  badge?: number;
};

export function SidebarLink({ to, icon: Icon, label, external = false, badge }: SidebarLinkProps) {
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
      {badge ? <strong className="sidebar-link__badge">{badge > 99 ? "99+" : badge}</strong> : null}
    </NavLink>
  );
}
