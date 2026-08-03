import { createServer } from "node:http";
import { createApplication } from "./app.js";

const { app, services, dispose } = await createApplication();
const server = createServer(app);

server.listen(services.config.port, "127.0.0.1", () => {
  const info = services.scanner.getInfo();
  console.log(`SMARTKID AI Project Assistant: http://127.0.0.1:${services.config.port}`);
  console.log(`Project root: ${info.root}`);
  console.log(`Indexed files: ${info.indexedFiles}`);
});

async function shutdown() {
  server.close();
  await dispose();
}

process.once("SIGINT", () => void shutdown().finally(() => process.exit(0)));
process.once("SIGTERM", () => void shutdown().finally(() => process.exit(0)));

export { createApplication } from "./app.js";
