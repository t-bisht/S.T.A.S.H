import { describe, expect, it } from "vitest";

import { errorMessages, resolveErrorMessage } from "./errorMessages";

describe("resolveErrorMessage", () => {
  it("returns null for null / undefined / empty", () => {
    expect(resolveErrorMessage(null)).toBeNull();
    expect(resolveErrorMessage(undefined)).toBeNull();
    expect(resolveErrorMessage("")).toBeNull();
  });

  it("resolves every known code to its exact message", () => {
    for (const [code, message] of Object.entries(errorMessages)) {
      expect(resolveErrorMessage(code)).toBe(message);
    }
  });

  it("signed_out copy never mentions Google, tokens or expiry", () => {
    expect(errorMessages.signed_out).toBe(
      "You've been signed out. Please sign in again to continue.",
    );
    expect(errorMessages.signed_out).not.toMatch(/google|token|expir/i);
  });

  it("falls back to internal_error for unknown codes", () => {
    expect(resolveErrorMessage("not_a_real_code")).toBe(
      errorMessages.internal_error,
    );
  });
});
