import { useState, type DragEvent } from "react";

type Options = {
  enabled?: boolean;
  onDropFile: (archivo: File) => void;
};

export function useDocumentDropZone({ enabled = true, onDropFile }: Options) {
  const [draggingDocument, setDraggingDocument] = useState(false);
  const canDropDocument = enabled && Boolean(onDropFile);

  const handleDragOver = (event: DragEvent<HTMLElement>) => {
    if (!canDropDocument || !event.dataTransfer.types.includes("Files")) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = "copy";
    setDraggingDocument(true);
  };

  const handleDragLeave = (event: DragEvent<HTMLElement>) => {
    if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
      setDraggingDocument(false);
    }
  };

  const handleDrop = (event: DragEvent<HTMLElement>) => {
    if (!canDropDocument) return;
    event.preventDefault();
    setDraggingDocument(false);
    const file = event.dataTransfer.files?.[0];
    if (!file) return;
    const allowed = file.type === "application/pdf"
      || file.type === "image/jpeg"
      || file.type === "image/png"
      || /\.(pdf|jpe?g|png)$/i.test(file.name);
    if (!allowed) {
      alert("Arrastra un archivo PDF, JPG o PNG para subirlo.");
      return;
    }
    onDropFile(file);
  };

  return {
    draggingDocument,
    dropZoneHandlers: {
      onDragEnter: handleDragOver,
      onDragLeave: handleDragLeave,
      onDragOver: handleDragOver,
      onDrop: handleDrop,
    },
  };
}
