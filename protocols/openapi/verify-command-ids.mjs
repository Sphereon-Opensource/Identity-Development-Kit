import { readdir, readFile } from "node:fs/promises";
import { dirname, extname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));
const commandIdPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*\.[a-z0-9]+(?:-[a-z0-9]+)*\.[a-z0-9]+(?:-[a-z0-9]+)*$/;
const violations = [];
let audited = 0;

async function visit(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  for (const entry of entries.sort((left, right) => left.name.localeCompare(right.name))) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      if (entry.name !== "external" && entry.name !== "node_modules") {
        await visit(path);
      }
      continue;
    }
    if (![".yaml", ".yml"].includes(extname(entry.name))) continue;

    const lines = (await readFile(path, "utf8")).split(/\r?\n/u);
    lines.forEach((line, index) => {
      const match = line.match(/^\s*x-command-id:\s*([^\s#]+).*$/u);
      if (!match) return;
      const commandId = match[1].replace(/^['"]|['"]$/gu, "");
      audited += 1;
      if (!commandIdPattern.test(commandId)) {
        violations.push(`${relative(root, path)}:${index + 1}: ${commandId}`);
      }
    });
  }
}

await visit(root);

if (violations.length > 0) {
  console.error("Every x-command-id must use exactly module.service.command segments:");
  violations.forEach((violation) => console.error(`  ${violation}`));
  process.exitCode = 1;
} else {
  console.log(`Validated ${audited} OpenAPI command IDs: all use module.service.command.`);
}
