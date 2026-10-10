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

  it("falls back to internal_error for unknown codes", () => {
    expect(resolveErrorMessage("not_a_real_code")).toBe(
      errorMessages.internal_error,
    );
  });
});
