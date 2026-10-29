import { defineConfig, loadEnv, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

export default defineConfig(({ mode }) => {
  const emitSourcemaps = mode === 'development'
  const configEnv = loadEnv(mode, process.cwd(), '')
  const backendContext = (process.env.BACKEND_CONTEXT ?? configEnv.BACKEND_CONTEXT ?? '/Math11').replace(/\/$/, '')

  return {
    build: {
      sourcemap: emitSourcemaps ? 'inline' : false,
      minify: !emitSourcemaps,
    },
    plugins: [react(), testServerLifecycle(mode), tailwindcss()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      proxy: {
        '/api': {
          target: process.env.BACKEND_URL || configEnv.BACKEND_URL || 'http://localhost:8080',
          changeOrigin: false,
          rewrite: (url) => `${backendContext}${url}`,
          cookiePathRewrite: '/',
        },
      },
      host: '0.0.0.0',
      port: parseInt(process.env.PORT || '8443'),
      strictPort: true,
    },
    preview: {
      host: '0.0.0.0',
      port: parseInt(process.env.PORT || '8443'),
    },
  }
})

/** Test-only lifecycle hook; never registered for normal development or builds. */
function testServerLifecycle(mode: string): Plugin {
  return {
    name: "lms-test-server-lifecycle",
    apply: "serve",
    configureServer(server) {
      const nonce = process.env.LMS_TEST_NONCE;
      if (!["test", "integration"].includes(mode) || !nonce) return;
      server.middlewares.use((req, res, next) => {
        if (req.url !== "/__lms_test_shutdown") return next();
        const local = ["127.0.0.1", "::1", "::ffff:127.0.0.1"].includes(req.socket.remoteAddress || "");
        if (!local || req.method !== "POST" || req.headers["x-lms-test-nonce"] !== nonce) { res.statusCode = 403; res.end(); return; }
        res.statusCode = 200; res.end("stopping");
        setTimeout(() => { void server.close().then(() => process.exit(0)); }, 50);
      });
    },
  };
}
