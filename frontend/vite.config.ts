import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const backendUrl = env.VITE_DEV_BACKEND_URL || "http://localhost:8081";

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        "/api": backendUrl,
        "/documentos": backendUrl,
      },
    },
  };
});
