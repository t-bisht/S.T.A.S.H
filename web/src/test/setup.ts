import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// Minimal window.ENV stub so modules referencing it at import time don't crash
// under jsdom. Individual tests may override specific keys.
if (!window.ENV) {
  window.ENV = {
    API_BASE_URL: "/api",
    HIEMDALL_BASE_URL: "http://localhost:9082",
    APP_ID: "stash",
  };
}

afterEach(() => {
  cleanup();
});
