import { spawnSync } from "node:child_process";

const scripts = [
  "signed-smoke.mjs",
  "account-transfer-smoke.mjs",
  "guild-flow-smoke.mjs",
  "counter-flow-smoke.mjs"
];

for (const script of scripts) {
  const result = spawnSync(process.execPath, [`./scripts/${script}`], {
    cwd: process.cwd(),
    env: process.env,
    stdio: "inherit"
  });
  if (result.status !== 0) process.exit(result.status ?? 1);
}

console.log(JSON.stringify({ ok: true, suites: scripts }, null, 2));
