# core/data test vectors (M06)

| File | SHA-256 | Produced by |
|---|---|---|
| `pbkdf2_sha256.json` | `3240c2d62b0c15c1...` | `tools/oracle/.venv/bin/python -I tools/oracle/gen_pbkdf2.py` (Python hashlib; `--check` verifies byte-identity) |
| `family_v1.json` | `7af09d7b500f148460193b0d339c9b11830d0fa5583cda899265ab1490ef5636` | B2 codec fixture: synthetic family data only |
| `snapshot_v1.bin` | `3dd6265750a20348712914f262c273cadf392ffbd8d97885a9f995e9d7a4d202` | `FrozenFixtures.snapshot()` |
| `export_v1.ppx` | `28c2cee868bdd85c6b986950c4b40976c22ad7a2682c93aa3ea58de6c1b9f0d1` | `FrozenFixtures.export()` |

## Frozen fixtures (regression lock, not an independent oracle)

`snapshot_v1.bin` and `export_v1.ppx` are the exact bytes of `family_v1.json` sealed with fixed parameters
that are used only in tests (`core/data/src/test/kotlin/.../backup/BackupTestKit.kt`, `FrozenFixtures`).
Production code always draws keys, salts and nonces from `SecureRandom`.

- Snapshot: K_b = bytes `00..1f`, generation 1, nonce `20..2b` (`SnapshotFormat.sealWithNonce`).
- Export: passphrase `pp-fixture-passphrase`, salt `00..0f`, nonce `10..1b`, 600 000 iterations
  (`ExportFormat.sealWithFixedParams`).

`SnapshotTest.frozenSnapshotDecodesToTheFixtureDocument` and `ExportFormatTest.frozenExportDecodesToTheFixtureDocument`
regenerate each file in memory and require byte equality with the frozen file. They then decrypt it and check it
equals `family_v1.json` byte for byte, and that it decodes to `FamilyFixture.doc`.

Generated once on 2026-11-15 with
`PP_WRITE_FIXTURES=1 gradle --offline :core:data:testDebugUnitTest --tests '*backup.SnapshotTest.frozen*' --tests '*backup.ExportFormatTest.frozen*'`.
Regenerate only for a deliberate format change (new version byte). Never regenerate to make a failing test pass.
Update the hashes above whenever you regenerate.
