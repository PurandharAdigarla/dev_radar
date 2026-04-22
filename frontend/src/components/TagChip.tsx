import Box from "@mui/material/Box";

export interface TagChipProps {
  label: string;
  selected: boolean;
  onToggle: () => void;
}

export function TagChip({ label, selected, onToggle }: TagChipProps) {
  return (
    <Box
      component="button"
      type="button"
      role="button"
      aria-pressed={selected}
      onClick={onToggle}
      sx={{
        display: "inline-flex",
        alignItems: "center",
        gap: "6px",
        fontFamily: "inherit",
        fontSize: 14,
        lineHeight: "20px",
        fontWeight: 500,
        padding: "6px 14px",
        borderRadius: 999,
        cursor: "pointer",
        userSelect: "none",
        border: "1px solid",
        borderColor: selected ? "text.primary" : "divider",
        bgcolor: selected ? "text.primary" : "background.paper",
        color: selected ? "#ffffff" : "text.primary",
        transition: "background 120ms, border-color 120ms, color 120ms",
        "&:hover": {
          bgcolor: selected ? "#000000" : "rgba(45,42,38,0.04)",
          borderColor: selected ? "text.primary" : "divider",
        },
        "&:focus-visible": {
          outline: "none",
          boxShadow: (t) => `0 0 0 2px ${t.palette.text.primary}`,
        },
      }}
    >
      {label}
    </Box>
  );
}
