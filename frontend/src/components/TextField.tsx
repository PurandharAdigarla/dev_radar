import MuiTextField, { type TextFieldProps as MuiTextFieldProps } from "@mui/material/TextField";
import { forwardRef } from "react";

export type TextFieldProps = MuiTextFieldProps;

export const TextField = forwardRef<HTMLDivElement, TextFieldProps>(function TextField(
  { fullWidth = true, InputLabelProps, InputProps, ...rest },
  ref,
) {
  // Our design keeps the label ABOVE the input (static, not floating).
  // MUI's default OutlinedInput still draws a `<fieldset><legend>` notch
  // for the floating-label cutout, which shows as an ugly gap at the top
  // of the input when the label is static. `notched: false` suppresses it.
  return (
    <MuiTextField
      ref={ref}
      fullWidth={fullWidth}
      InputLabelProps={{ shrink: true, ...InputLabelProps }}
      InputProps={{ notched: false, ...InputProps }}
      {...rest}
    />
  );
});
