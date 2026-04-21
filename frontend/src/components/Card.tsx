import MuiCard, { type CardProps as MuiCardProps } from "@mui/material/Card";
import MuiCardContent from "@mui/material/CardContent";
import { forwardRef, type ReactNode } from "react";

export interface CardProps extends Omit<MuiCardProps, "children"> {
  children: ReactNode;
  padded?: boolean;
}

export const Card = forwardRef<HTMLDivElement, CardProps>(function Card(
  { children, padded = true, variant = "outlined", ...rest },
  ref,
) {
  return (
    <MuiCard ref={ref} variant={variant} {...rest}>
      {padded ? <MuiCardContent>{children}</MuiCardContent> : children}
    </MuiCard>
  );
});
