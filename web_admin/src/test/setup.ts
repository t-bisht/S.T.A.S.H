import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// No window.ENV here (unlike web/): the admin UI is same-origin with its API
// in production and proxied in dev, so every API path is relative.
afterEach(() => {
  cleanup();
});
