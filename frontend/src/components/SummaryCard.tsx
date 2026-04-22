import Paper from "@mui/material/Paper";
import Typography from "@mui/material/Typography";

interface SummaryCardProps {
  label: string;
  value: string;
  sub?: string;
}

export function SummaryCard({ label, value, sub }: SummaryCardProps) {
  return (
    <Paper
      sx={{
        p: "20px 24px",
        border: 1,
        borderColor: "divider",
        borderRadius: 2,
      }}
    >
      <Typography
        sx={{
          fontSize: "0.8125rem",
          lineHeight: "20px",
          color: "text.secondary",
          mb: 0.5,
        }}
      >
        {label}
      </Typography>
      <Typography
        sx={{
          fontSize: "1.75rem",
          lineHeight: "36px",
          fontWeight: 500,
          letterSpacing: "-0.01em",
          color: "text.primary",
        }}
      >
        {value}
      </Typography>
      {sub && (
        <Typography
          sx={{
            fontSize: "0.8125rem",
            lineHeight: "20px",
            color: "text.secondary",
            mt: 0.5,
          }}
        >
          {sub}
        </Typography>
      )}
    </Paper>
  );
}
