import { ChevronLeft, ChevronRight } from "lucide-react";

export function PaginationBar({ page, totalPages, totalItems, pageSize, onPageChange, onPageSizeChange }: { page: number; totalPages: number; totalItems: number; pageSize: number; onPageChange: (page: number) => void; onPageSizeChange: (size: number) => void }) {
  if (totalItems === 0) return null;
  return <div className="pagination-bar"><span>{totalItems} registros</span><label><span>Por pagina</span><select value={pageSize} onChange={(event) => onPageSizeChange(Number(event.target.value))}><option value={10}>10</option><option value={25}>25</option><option value={50}>50</option><option value={100}>100</option></select></label><div className="pagination-bar__nav"><button className="icon-button" disabled={page <= 0} onClick={() => onPageChange(page - 1)} title="Pagina anterior" type="button"><ChevronLeft size={17} /></button><strong>{page + 1} / {Math.max(totalPages, 1)}</strong><button className="icon-button" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)} title="Pagina siguiente" type="button"><ChevronRight size={17} /></button></div></div>;
}
