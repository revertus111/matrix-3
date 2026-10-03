# Matrix3 Client Semantic Renames

## Purpose

This file preserves the translation between original obfuscated/decompiled Matrix3 client symbols and approved readable semantic names.

It exists so the source can become clean and understandable without losing correlation to old commits, donor/decompiled source, older documentation, another revision, or historical debugging notes that still use the original identifiers.

## Rules

- Only record approved semantic renames.
- Do not rename or record `UNKNOWN` / unproven `HYPOTHESIS` symbols as established semantics.
- Preserve the exact original class/field/method identifier.
- Record the current readable symbol.
- Record `verified-static` or `VERIFIED` evidence classification used to justify the rename.
- Include a short behavior/reason summary explaining what the code does.
- Add the rename commit SHA when available.
- If stronger evidence changes a semantic name, update the existing entry rather than creating a competing mapping.
- Source comments should preserve the original symbol where useful for correlation.
- `CLIENT_MAP.md` remains the broader system/ownership map; this file is only the old-name -> current-name ledger.

## Entry format

```text
### <Current readable symbol>

Original: <original obfuscated/decompiled symbol>
Current: <current readable symbol>
Evidence: VERIFIED | verified-static
Behavior: <concise explanation of what the code does>
Commit: <sha or pending>
Related: <optional CLIENT_MAP subsystem / related symbols>
```

## Renames

No semantic renames have been normalized into this ledger yet.

Existing source names must not be retroactively guessed here. Add entries as established mappings are deliberately renamed and verified.
