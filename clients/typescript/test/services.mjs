// The services the package must export: every `service` the .proto files under a directory declare (the published
// protos the package is generated from), as `package.Service`. Also a command: `node test/services.mjs <proto dir>`
// prints them comma-separated, for the registry smoke in .github/workflows/client.yml.
import { readdirSync, readFileSync, realpathSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

export function declaredServices(protoDir) {
	const found = new Set();
	const walk = (dir) => {
		for (const entry of readdirSync(dir, { withFileTypes: true })) {
			const path = join(dir, entry.name);
			if (entry.isDirectory()) walk(path);
			else if (entry.name.endsWith('.proto')) {
				// Strings, line comments and block comments in one pass, in source order: a `/*` inside a string
				// ("image/*") must not open a comment that swallows the declarations up to a later `*/`.
				const text = readFileSync(path, 'utf8').replace(
					/"(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*'|\/\/[^\n]*|\/\*[\s\S]*?\*\//g,
					(match) => (match[0] === '/' ? '' : '""'),
				);
				const pkg = /^\s*package\s+([\w.]+)\s*;/m.exec(text)?.[1];
				for (const match of text.matchAll(/^\s*service\s+(\w+)\s*\{/gm)) found.add(pkg ? `${pkg}.${match[1]}` : match[1]);
			}
		}
	};
	walk(protoDir);
	return found;
}

if (process.argv[1] && import.meta.url === pathToFileURL(realpathSync(process.argv[1])).href) {
	console.log([...declaredServices(process.argv[2] ?? 'proto')].sort().join(','));
}
