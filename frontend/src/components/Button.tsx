import MuiButton, { type ButtonProps as MuiButtonProps } from "@mui/material/Button";
import { forwardRef } from "react";

export type ButtonProps = MuiButtonProps;

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = "contained", color = "primary", ...rest },
  ref,
) {
  return <MuiButton ref={ref} variant={variant} color={color} {...rest} />;
});
