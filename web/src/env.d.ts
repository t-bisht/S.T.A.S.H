/**
 * Runtime config injected into window by /env.js before the module bundle loads.
 * In dev, served from web/public/env.js. In prod, written by docker-entrypoint.sh.
 */
export interface StashEnv {
  readonly API_BASE_URL: string;
  readonly HIEMDALL_BASE_URL: string;
  readonly APP_ID: string;
}

declare global {
  interface Window {
    ENV: StashEnv;
  }
}

export {};
