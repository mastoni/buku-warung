import { buildApp } from './app.js';
import { config } from './config/index.js';
import { getDatabase, closeDatabase } from './db/database.js';

const app = buildApp();

async function start() {
  try {
    // Initialize DB
    getDatabase();

    await app.listen({ port: config.port, host: config.host });
    console.log(`[Buku Warung License Server] running on http://${config.host}:${config.port}`);
  } catch (err) {
    app.log.error(err);
    process.exit(1);
  }
}

// Graceful shutdown
const signals: NodeJS.Signals[] = ['SIGINT', 'SIGTERM'];
signals.forEach((signal) => {
  process.on(signal, async () => {
    console.log(`Received ${signal}. Gracefully shutting down...`);
    await app.close();
    closeDatabase();
    process.exit(0);
  });
});

start();
