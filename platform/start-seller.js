const http = require('http');
const path = require('path');
const fs = require('fs');

const dir = fs.realpathSync(path.resolve(__dirname, '../apps/seller'));
const next = require(path.join(dir, 'node_modules/next'));

const dev = process.env.NODE_ENV !== 'production';
const app = next({ dev, dir, hostname: '0.0.0.0', port: 3003 });
const handle = app.getRequestHandler();

console.log(`[Seller] Initializing Next.js app in ${dir}...`);

let isPrepared = false;
let prepareError = null;
const preparePromise = app.prepare().then(() => {
  isPrepared = true;
  console.log('[Seller] Next.js compilation engine ready.');
}).catch((err) => {
  prepareError = err;
  console.error('[Seller] Preparation error:', err);
});

const server = http.createServer(async (req, res) => {
  try {
    if (prepareError) {
      res.writeHead(500, { 'Content-Type': 'text/plain' });
      res.end(`Next.js preparation error: ${prepareError.message}`);
      return;
    }
    if (!isPrepared) {
      await preparePromise;
    }
    return handle(req, res);
  } catch (err) {
    console.error('[Seller] Request handling error:', err);
    res.writeHead(500, { 'Content-Type': 'text/plain' });
    res.end(`Server error: ${err.message}`);
  }
});

server.listen(3003, '0.0.0.0', () => {
  console.log('🚀 Seller Dashboard successfully listening on http://localhost:3003 and http://0.0.0.0:3003');
});
