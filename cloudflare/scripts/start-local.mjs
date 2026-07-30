import { openSync } from "node:fs";
import { spawn } from "node:child_process";

const stdout = openSync("./wrangler-local.out.log", "a");
const stderr = openSync("./wrangler-local.err.log", "a");
const child = spawn(
  process.execPath,
  ["./node_modules/wrangler/bin/wrangler.js", "dev", "--local", "--port", "8787"],
  {
    cwd: process.cwd(),
    detached: true,
    windowsHide: true,
    stdio: ["ignore", stdout, stderr]
  }
);
child.unref();
console.log(child.pid);
