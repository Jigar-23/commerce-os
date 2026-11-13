/**
 * Commerce OS — Production PM2 Cluster & Worker Ecosystem Configuration
 * 
 * Usage:
 *   pm2 start ecosystem.config.js --env production
 *   pm2 reload ecosystem.config.js --update-env (Zero-Downtime Rolling Update)
 *   pm2 monit
 */

module.exports = {
  apps: [
    {
      name: 'commerceos-api',
      script: './platform/server/production-server.js',
      instances: 'max', // Scales to all available CPU cores
      exec_mode: 'cluster',
      autorestart: true,
      watch: false,
      max_memory_restart: '800M',
      listen_timeout: 8000,
      kill_timeout: 5000,
      env_production: {
        NODE_ENV: 'production',
        COMMERCEOS_PROCESS_ROLE: 'API',
        PORT: 8083
      }
    },
    {
      name: 'commerceos-worker',
      script: './platform/server/production-server.js',
      instances: 1, // Single instance for outbox dispatch to prevent duplicate polling contention
      exec_mode: 'fork',
      autorestart: true,
      watch: false,
      max_memory_restart: '1G',
      env_production: {
        NODE_ENV: 'production',
        COMMERCEOS_PROCESS_ROLE: 'WORKER',
        PORT: 8084
      }
    }
  ]
};
