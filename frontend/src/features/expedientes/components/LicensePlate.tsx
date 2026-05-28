type Props = {
  value?: string | null;
};

export function LicensePlate({ value }: Props) {
  return (
    <div className="license-plate" aria-label="Matrícula del expediente">
      {value || "SIN MATRÍCULA"}
    </div>
  );
}
