import type { ReactNode } from "react";

type ListPageChromeProps = {
  eyebrow: string;
  title: string;
  summary: string;
  count: string;
  action?: ReactNode;
  children: ReactNode;
};

export function ListPageChrome({ eyebrow, title, summary, count, action, children }: ListPageChromeProps) {
  return (
    <section className="records-page">
      <header className="records-header">
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h2>{title}</h2>
          <p>{summary}</p>
        </div>
        <div className="records-header__actions">
          <span className="records-count">{count}</span>
          {action}
        </div>
      </header>

      {children}
    </section>
  );
}
