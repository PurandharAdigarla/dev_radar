import MuiButton, { type ButtonProps as MuiButtonProps } from "@mui/material/Button";
import { motion } from "framer-motion";
import type { ElementType } from "react";

export type ButtonProps<C extends ElementType = "button"> = MuiButtonProps<C, { component?: C }>;

export function Button<C extends ElementType = "button">(props: ButtonProps<C>) {
  const { variant = "contained", color = "primary", ...rest } = props;
  return (
    <motion.div
      whileHover={{ y: -1, scale: 1.01 }}
      whileTap={{ scale: 0.98 }}
      transition={{ type: "spring", stiffness: 400, damping: 20 }}
      style={{ display: "inline-block" }}
    >
      <MuiButton variant={variant} color={color} {...(rest as MuiButtonProps)} />
    </motion.div>
  );
}
