// Shared utility — Tailwind class composition.
// `clsx` resolves conditional branches; `twMerge` resolves conflicting
// Tailwind utilities so callers can override classes without worrying
// about declaration order.

import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
