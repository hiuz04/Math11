import { randomUUID } from "node:crypto";
import { defineConfig } from "@playwright/test";
const port=Number(process.env.LMS_TEST_PORT || 8445);
const lmsTestNonce=randomUUID();
export default defineConfig({
  metadata: { lmsTestNonce },
  globalTeardown: "./tests/stop-test-server.ts",
  testDir: "./tests/browser-api", outputDir: "test-results-api", timeout: 30000, workers: 1,
  use: { baseURL: `http://127.0.0.1:${port}`, headless: true, screenshot: "only-on-failure" },
  webServer: { env: { LMS_TEST_NONCE: lmsTestNonce }, command: `node node_modules/vite/bin/vite.js --mode integration --host 127.0.0.1 --port ${port}`, url: `http://127.0.0.1:${port}`, reuseExistingServer: !process.env.CI, timeout: 60000 },
});
