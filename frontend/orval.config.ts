import { defineConfig } from "orval";

export default defineConfig({
  trackingApi: {
    input: {
      target: "../api/openapi.yml",
    },
    output: {
      target: "./src/api/tracking.ts",
      client: "fetch",
      mode: "single",
      baseUrl: "",
    },
  },
});
