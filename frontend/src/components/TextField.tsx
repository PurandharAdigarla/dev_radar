import MuiTextField, { type TextFieldProps as MuiTextFieldProps } from "@mui/material/TextField";
import { forwardRef } from "react";

export type TextFieldProps = MuiTextFieldProps;

export const TextField = forwardRef<HTMLDivElement, TextFieldProps>(function TextField(
  { fullWidth = true, ...rest },
  ref,
) {
  return <MuiTextField ref={ref} fullWidth={fullWidth} {...rest} />;
});
