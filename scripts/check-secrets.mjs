#!/usr/bin/env node

import { execFileSync } from 'node:child_process'
import fs from 'node:fs'

const files = execFileSync(
  'git',
  ['ls-files', '--cached', '--others', '--exclude-standard', '-z'],
  { encoding: 'utf8' }
).split('\0').filter(Boolean)
const binaryExtensions = /\.(?:png|jpe?g|gif|ico|woff2?|ttf|xlsx|jar)$/i
const patterns = [
  { name: 'private key', regex: /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/ },
  { name: 'OpenAI-style API key', regex: /\bsk-[A-Za-z0-9]{20,}\b/ },
  { name: 'GitHub token', regex: /\bgh[pousr]_[A-Za-z0-9]{20,}\b/ },
  { name: 'AWS access key', regex: /\bAKIA[0-9A-Z]{16}\b/ }
]

const findings = []
for (const file of files) {
  if (binaryExtensions.test(file) || !fs.existsSync(file)) continue
  const text = fs.readFileSync(file, 'utf8')
  for (const pattern of patterns) {
    if (pattern.regex.test(text)) findings.push(`${file}: ${pattern.name}`)
  }
}

if (findings.length) {
  console.error(`Worktree secret scan failed:\n${findings.join('\n')}`)
  process.exitCode = 1
} else {
  console.log(`Worktree secret scan passed (${files.length} tracked and unignored files checked)`)
}
