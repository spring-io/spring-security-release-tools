// ncc emits an ES module for this package ("type": "module"), but the CommonJS
// dependencies it bundles still call require() for node builtins. Define it.
import { readFileSync, writeFileSync } from 'node:fs'

const file = process.argv[2] ?? 'dist/index.js'
const shim = `import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
`
const source = readFileSync(file, 'utf8')
if (!source.startsWith(shim)) {
  writeFileSync(file, shim + source)
}
