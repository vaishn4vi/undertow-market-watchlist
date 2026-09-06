interface Row {
  label: string;
  value: number;
  color: string;
}

export function MovementComparisonChart({
  stockReturn,
  sectorReturn,
  expectedReturn,
}: {
  stockReturn: number;
  sectorReturn: number;
  expectedReturn: number;
}) {
  const rows: Row[] = [
    { label: "Stock (actual)", value: stockReturn, color: "var(--color-accent)" },
    { label: "Sector", value: sectorReturn, color: "var(--color-text-muted)" },
    { label: "Expected (from sector fit)", value: expectedReturn, color: "var(--color-text-faint)" },
  ];

  const maxMagnitude = Math.max(2, ...rows.map((r) => Math.abs(r.value)));

  return (
    <div className="flex flex-col gap-3">
      {rows.map((row) => (
        <div key={row.label} className="flex items-center gap-3">
          <span className="w-36 shrink-0 text-xs text-[var(--color-text-faint)]">{row.label}</span>
          <div className="relative h-5 flex-1">
            {/* zero line */}
            <div className="absolute inset-y-0 left-1/2 w-px bg-[var(--color-border-strong)]" />
            <div
              className="absolute inset-y-0 rounded-sm transition-all duration-500"
              style={{
                backgroundColor: row.color,
                left: row.value >= 0 ? "50%" : `${50 - (Math.abs(row.value) / maxMagnitude) * 50}%`,
                width: `${(Math.abs(row.value) / maxMagnitude) * 50}%`,
              }}
            />
          </div>
          <span
            className="w-16 shrink-0 text-right text-xs font-medium tabular"
            style={{ color: row.value >= 0 ? "var(--color-positive)" : "var(--color-negative)" }}
          >
            {row.value >= 0 ? "+" : ""}
            {row.value.toFixed(2)}%
          </span>
        </div>
      ))}
    </div>
  );
}
