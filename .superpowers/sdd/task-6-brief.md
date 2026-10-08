### Task 6: Docs + progress closeout

**Files:**
- Modify: `phase1-progress.md` â€” mark Phase 1m; Next â†’ EDK artifact pilot / selective packs in daily use
- Modify: `phase1m-gates.md` â€” full gate table
- Modify: `docs/superpowers/specs/2026-09-19-idk-thin-root-hybrid-packs-design.md` â€” status `implemented` (pilot)
- Test: doc-only review

- [ ] **Step 1: Complete gate table**

| Gate | Expectation |
| --- | --- |
| M1 Helper extract | ps1 PASS |
| M2 Thin root | pack modules absent from root `projects` |
| M3 Core `-p` smoke | BUILD SUCCESSFUL |
| M4 Protocols `-p` smoke | BUILD SUCCESSFUL |
| M5 Infra artifacts | USE_LOCAL_IDK=false no pack configure |
| M6 Infra selective | IDK_LOCAL_PACKS=core prints pack include |
| M7 Invalid pack | configuration fails |

- [ ] **Step 2: Update progress Next**

```markdown
## Also done (Phase 1m â€” thin root + hybrid packs)
- Spec: docs/superpowers/specs/2026-09-19-idk-thin-root-hybrid-packs-design.md
- Plan: docs/superpowers/plans/2026-09-19-idk-thin-root-hybrid-packs.md
- Gates: phase1m-gates.md

## Next
- EDK artifact-mode pilot (authorized separately)
- Habituate IDK_LOCAL_PACKS in local VDX-infra workflows; keep enterprise on USE_LOCAL_IDK=false
```

- [ ] **Step 3: Commit**

```bash
git add phase1-progress.md phase1m-gates.md docs/superpowers/specs/2026-09-19-idk-thin-root-hybrid-packs-design.md
git commit -m "Close phase 1m thin-root hybrid pack gates and progress."
```

---

