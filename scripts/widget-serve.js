// Minimal static server for local widget testing on the Portal.
// Usage: node scripts/widget-serve.js [port]   (serves the repo's widgets/ dir)
const http = require("http");
const fs = require("fs");
const path = require("path");

const port = parseInt(process.argv[2] || "8099", 10);
const root = path.join(__dirname, "..", "widgets");
const TYPES = {
  ".html": "text/html; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".png": "image/png",
  ".svg": "image/svg+xml",
};

http
  .createServer((req, res) => {
    try {
      let rel = decodeURIComponent(req.url.split("?")[0]);
      if (rel === "/" || rel === "") rel = "/index.html";
      let file = path.normalize(path.join(root, rel));
      if (!file.startsWith(root)) { res.writeHead(403).end("forbidden"); return; }
      if (fs.existsSync(file) && fs.statSync(file).isDirectory()) file = path.join(file, "index.html");
      if (!fs.existsSync(file)) { res.writeHead(404).end("not found: " + rel); return; }
      res.writeHead(200, {
        "Content-Type": TYPES[path.extname(file)] || "application/octet-stream",
        "Access-Control-Allow-Origin": "*",
        "Cache-Control": "no-store",
      });
      fs.createReadStream(file).pipe(res);
    } catch (e) {
      res.writeHead(500).end(String(e));
    }
  })
  .listen(port, "0.0.0.0", () => console.log(`widgets served on http://0.0.0.0:${port} from ${root}`));
