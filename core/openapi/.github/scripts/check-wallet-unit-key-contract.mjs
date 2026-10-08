#!/usr/bin/env node

import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const openapi = readFileSync(resolve(repositoryRoot, 'wallet-unit-openapi.yml'), 'utf8')
  .replace(/\r\n?/g, '\n')
const components = readFileSync(resolve(repositoryRoot, 'wallet-unit-components.yml'), 'utf8')
  .replace(/\r\n?/g, '\n')

function schemaBlock(name) {
  const marker = `\n    ${name}:\n`
  const start = components.indexOf(marker)
  assert.notEqual(start, -1, `Missing Wallet Unit schema: ${name}`)
  const contentStart = start + marker.length
  const nextSchema = components.slice(contentStart).search(/\n    [A-Za-z0-9][A-Za-z0-9_]*:\n/)
  return components.slice(contentStart, nextSchema === -1 ? undefined : contentStart + nextSchema)
}

function directProperties(block) {
  const propertiesMarker = '\n      properties:\n'
  const propertiesStart = block.indexOf(propertiesMarker)
  assert.notEqual(propertiesStart, -1, 'Schema has no properties block')
  return [...block.slice(propertiesStart + propertiesMarker.length).matchAll(/^        ([A-Za-z][A-Za-z0-9_]*):/gm)]
    .map((match) => match[1])
}

function inlineRequired(block) {
  const match = block.match(/^      required:\s*\[([^\]]+)\]\s*$/m)
  assert.ok(match, 'Schema must declare one explicit inline required set')
  return match[1].split(',').map((entry) => entry.trim())
}

const keyPathStart = openapi.indexOf('\n  /units/{walletUnitId}/wscd/keys:\n')
const keyPathEnd = openapi.indexOf('\n  /wscd/raw-sign:\n')
assert.ok(keyPathStart >= 0 && keyPathEnd > keyPathStart, 'Wallet Unit key-management paths are missing')
const keyPaths = openapi.slice(keyPathStart, keyPathEnd)

const commandIds = [...keyPaths.matchAll(/^      x-command-id:\s*(\S+)\s*$/gm)]
  .map((match) => match[1])
assert.deepEqual(commandIds, [
  'wallet.wscd.provision-key',
  'wallet.wscd.list-keys',
  'wallet.wscd.get-key',
])
for (const commandId of commandIds) {
  assert.equal(commandId.split('.').length, 3, `${commandId} must remain a three-segment command ID`)
}

const operationIds = [...keyPaths.matchAll(/^      operationId:\s*(\S+)\s*$/gm)]
  .map((match) => match[1])
assert.deepEqual(operationIds, [
  'provisionWalletUnitWscdKeyReference',
  'listWalletUnitWscdKeyReferences',
  'getWalletUnitWscdKeyReference',
])

const methods = [...keyPaths.matchAll(/^    (get|post|put|patch|delete):\s*$/gm)]
  .map((match) => match[1])
assert.deepEqual(methods, ['post', 'get', 'get'])
assert.doesNotMatch(keyPaths, /^  \/.*(?:provider|handle|register|delete)/gim)

const algorithmProfile = schemaBlock('WalletUnitWscdAlgorithmProfile')
assert.match(algorithmProfile, /^      enum:\s*\[holder-proof-es256\]\s*$/m)

const provisionRequest = schemaBlock('ProvisionWalletUnitWscdKeyRequest')
assert.deepEqual(directProperties(provisionRequest), ['idempotencyKey', 'algorithmProfileId'])
assert.deepEqual(inlineRequired(provisionRequest), ['idempotencyKey', 'algorithmProfileId'])

const publicJwk = schemaBlock('WalletUnitWscdPublicJwk')
const publicJwkProperties = ['kty', 'crv', 'x', 'y', 'kid', 'alg', 'use', 'key_ops']
assert.deepEqual(directProperties(publicJwk), publicJwkProperties)
assert.deepEqual(inlineRequired(publicJwk), publicJwkProperties)
assert.match(publicJwk, /^      additionalProperties:\s*false\s*$/m)
for (const privateField of ['d', 'p', 'q', 'dp', 'dq', 'qi', 'oth', 'k']) {
  assert.ok(!publicJwkProperties.includes(privateField), `Private JWK field is forbidden: ${privateField}`)
}

const keyDetail = schemaBlock('WalletUnitWscdKeyDetail')
const detailProperties = [
  'keyRef',
  'kid',
  'algorithmProfileId',
  'algorithm',
  'keyType',
  'state',
  'availableActions',
  'publicJwk',
]
assert.deepEqual(directProperties(keyDetail), detailProperties)
assert.deepEqual(inlineRequired(keyDetail), detailProperties)
assert.match(keyDetail, /^      additionalProperties:\s*false\s*$/m)
assert.match(keyDetail, /pattern:\s*['"]\^wscd_/)

const publicContractFields = [
  ...directProperties(provisionRequest),
  ...publicJwkProperties,
  ...detailProperties,
]
for (const field of publicContractFields) {
  assert.doesNotMatch(field, /provider|handle|locator|resource|alias|private|register|delete/i)
}

console.log('Wallet Unit WSCD key OpenAPI source contract: PASS')
