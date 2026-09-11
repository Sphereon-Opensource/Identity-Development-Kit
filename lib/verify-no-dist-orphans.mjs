/**
 * Dist-orphan guard: every compiled module must still have a source.
 *
 * `tsc` never removes output for a source file that was deleted, and these packages publish their
 * whole `dist` via the `files` field. So a deleted module keeps shipping: it typechecks and it
 * executes for anyone who imports it, and it only breaks on a fresh clone or in CI.
 *
 * This was not hypothetical. `theme-client.{js,d.ts,js.map,d.ts.map}` sat in the theme-react dist
 * from 13 March for a source file that no longer existed, went out in every tarball, and declared
 * an outdated type union that misled someone grepping the installed package.
 *
 * Note what a freshness check does NOT cover, because the two are easy to conflate: comparing
 * source mtimes against dist mtimes proves dist is not STALE, and says nothing about dist holding
 * modules the source no longer has. An orphan has no source to be newer than it, so it never enters
 * a source-driven walk. Fresh and superset are compatible states.
 *
 * Wired to `prepack` so it runs on `npm pack` rather than when someone remembers.
 *
 * Run: node <path>/verify-no-dist-orphans.mjs <packageDir>
 */

import {readdirSync, existsSync} from 'node:fs'
import {join, relative, sep, resolve} from 'node:path'

const pkg = resolve(process.argv[2] ?? '.')
const dist = join(pkg, 'dist')
const src = join(pkg, 'src')

const norm = (p) => p.split(sep).join('/')

function walk(dir, base, out = []) {
  if (!existsSync(dir)) return out
  for (const entry of readdirSync(dir, {withFileTypes: true})) {
    const p = join(dir, entry.name)
    if (entry.isDirectory()) walk(p, base, out)
    else out.push(norm(relative(base, p)))
  }
  return out
}

if (!existsSync(dist)) {
  console.log(`v dist-orphans: no dist in ${pkg}, nothing to check`)
  process.exit(0)
}
if (!existsSync(src)) {
  console.log(`x dist-orphans: ${pkg} has a dist but no src, so every module would count as an orphan; refusing to guess`)
  process.exit(1)
}

const distModules = walk(dist, dist).filter((f) => f.endsWith('.js'))
const sources = new Set(walk(src, src))

// A parse that finds nothing would let the check below pass over an empty set, which is the same
// silent success this file exists to prevent.
if (distModules.length === 0) {
  console.log(`x dist-orphans: found 0 compiled modules under ${dist}, expected many; has the build run, or did the layout move?`)
  process.exit(1)
}
if (sources.size === 0) {
  console.log(`x dist-orphans: found 0 source files under ${src}, expected many`)
  process.exit(1)
}

const orphans = distModules.filter((f) => {
  const stem = f.replace(/\.js$/, '')
  return !sources.has(`${stem}.ts`) && !sources.has(`${stem}.tsx`)
})

if (orphans.length) {
  console.log(`x dist-orphans: ${orphans.length} compiled module(s) with no source, in ${pkg}`)
  for (const o of orphans) console.log(`    dist/${o}`)
  console.log('  These ship in the tarball and are importable by consumers. Delete dist and rebuild.')
  process.exit(1)
}

console.log(`v dist-orphans: ${distModules.length} modules, every one has a source`)
