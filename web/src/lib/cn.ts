// ─── cn.ts ─────────────────────────────────────────────────────
// Tiny helper to compose Tailwind class strings safely.
// `clsx` handles conditional classes; `twMerge` resolves conflicting
// Tailwind utilities (e.g. `px-2 px-4` → `px-4`) so a caller can
// override classes without worrying about order.

import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
