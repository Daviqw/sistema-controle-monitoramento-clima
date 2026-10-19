// Servidor local: entrega o Angular compilado e encaminha apenas a API.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../frontend/dist/clima-frontend/browser');
const origin = new URL(process.env.FRONTEND_ORIGIN || 'http://localhost:4200');
if (origin.protocol !== 'http:' || !['localhost', '127.0.0.1'].includes(origin.hostname)) {
  throw new Error('Este iniciador atende somente HTTP local (localhost ou 127.0.0.1).');
}
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.png': 'image/png', '.woff2': 'font/woff2' };
const server = http.createServer((req, res) => {
  if (req.headers.host !== origin.host) { res.writeHead(400).end(); return; }
  if (/^\/(api|actuator)(\/|\?|$)/.test(req.url)) {
    const headers = { ...req.headers, host: `127.0.0.1:${process.env.SERVER_PORT || 8080}`, origin: origin.origin,
      'x-frontend-key': process.env.FRONTEND_SHARED_KEY || 'dev-only-frontend-key' };
    const upstream = http.request({ hostname: '127.0.0.1', port: process.env.SERVER_PORT || 8080, path: req.url, method: req.method, headers }, response => {
      res.writeHead(response.statusCode, response.headers);
      response.pipe(res);
    });
    upstream.setTimeout(60000, () => upstream.destroy());
    upstream.on('error', () => { if (!res.headersSent) res.writeHead(502); res.end('Backend indisponivel. Inicie o backend e tente novamente.'); });
    req.on('aborted', () => upstream.destroy());
    res.on('close', () => upstream.destroy());
    req.pipe(upstream);
    return;
  }
  if (!['GET', 'HEAD'].includes(req.method)) { res.writeHead(405).end(); return; }
  let pathname;
  try { pathname = decodeURIComponent(req.url.split('?')[0]); } catch { res.writeHead(400).end(); return; }
  let file = path.resolve(root, '.' + pathname);
  if (!file.startsWith(root + path.sep) && file !== root) { res.writeHead(403).end(); return; }
  fs.stat(file, (err, stat) => {
    if (err || !stat.isFile()) {
      if (path.extname(pathname)) { res.writeHead(404).end(); return; }
      file = path.join(root, 'index.html');
    }
    const stream = fs.createReadStream(file);
    stream.on('error', () => { if (!res.headersSent) res.writeHead(404); res.end(); });
    stream.on('open', () => {
      res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream',
        'Cache-Control': 'no-cache', 'X-Content-Type-Options': 'nosniff' });
      if (req.method === 'HEAD') { stream.destroy(); res.end(); } else stream.pipe(res);
    });
  });
});
server.on('error', error => { console.error(error.code === 'EADDRINUSE' ? 'Porta ocupada. Feche a interface anterior antes de iniciar outra.' : error.message); process.exitCode = 1; });
server.listen(Number(origin.port || 80), origin.hostname, () => console.log(`Sistema disponivel em ${origin.origin}. Ctrl+C para encerrar.`));
