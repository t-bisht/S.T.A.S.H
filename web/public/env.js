// Dev-local runtime config. Served by Vite as-is from web/public/.
// Container runtime (docker-entrypoint.sh) overwrites /env.js on startup.
window.ENV = {
  API_BASE_URL: "/api",
  HIEMDALL_BASE_URL: "http://localhost:9082",
  APP_ID: "stash",
};
