import type { FullConfig } from "@playwright/test";
/** Stop only the Vite instance started with this test run's private nonce. */
export default async function stopTestServer(config: FullConfig) {
  const origin = config.projects[0]?.use.baseURL;
  const nonce = config.metadata.lmsTestNonce;
  if (!origin || typeof nonce !== "string") return;
  try { await fetch(new URL("/__lms_test_shutdown", origin), { method: "POST", headers: { "X-LMS-Test-Nonce": nonce }, signal: AbortSignal.timeout(3000) }); }
  catch { /* A reused or already stopped server belongs to its original owner. */ }
}
