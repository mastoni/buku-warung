module.exports = {
  apps: [
    {
      name: 'bukuwarung-license-server',
      script: 'dist/server.js',
      cwd: '/var/www/bukuwarung-license',
      instances: 1, // Single authoritative instance for SQLite database
      exec_mode: 'fork',
      autorestart: true,
      watch: false,
      max_memory_restart: '300M',
      env_production: {
        NODE_ENV: 'production',
        HOST: '127.0.0.1',
        PORT: 3100, // Dedicated isolated port to prevent Docker port 3000 conflicts
        DATABASE_PATH: '/var/data/bukuwarung-license/license.db',
        RATE_LIMIT_MAX: 120,
        RATE_LIMIT_WINDOW_MS: 60000
      }
    }
  ]
};
