import { buildAdminApp } from './app.js';
import { config } from './config/index.js';

const app = buildAdminApp();

async function start() {
  try {
    await app.listen({ port: config.port, host: config.host });
    console.log(`[Buku Warung License Admin] running on http://${config.host}:${config.port}`);
  } catch (err) {
    app.log.error(err);
    process.exit(1);
  }
}

const signals: NodeJS.Signals[] = ['SIGINT', 'SIGTERM'];
signals.forEach((signal) => {
  process.on(signal, async () => {
    console.log(`Received ${signal}. Gracefully shutting down admin server...`);
    await app.close();
    process.exit(0);
  });
});

start();
