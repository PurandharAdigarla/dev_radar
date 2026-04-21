import MuiButton, { type ButtonProps as MuiButtonProps } from "@mui/material/Button";
import type { ElementType } from "react";

export type ButtonProps<C extends ElementType = "button"> = MuiButtonProps<C, { component?: C }>;

export function Button<C extends ElementType = "button">(props: ButtonProps<C>) {
  const { variant = "contained", color = "primary", ...rest } = props;
  return <MuiButton variant={variant} color={color} {...(rest as MuiButtonProps)} />;
}
