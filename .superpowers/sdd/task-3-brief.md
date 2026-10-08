### Task 3: Pack smoke after thin root

**Files:**
- Create: `phase1m-gates.md` (partial)
- Test: compile smokes

**Interfaces:**
- Consumes: thin root + existing pack settings
- Produces: gate evidence that packs still build standalone

- [ ] **Step 1: Core compile**

```powershell
cd <idk-worktree>
$env:GRADLE_USER_HOME = "$pwd\.gradle-home-solo"
.\gradlew.bat -p core compileKotlinJvm --no-configuration-cache --console=plain -Dkmp.targets=jvm --max-workers=4
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Protocols compile (artifacts for lower packs)**

```powershell
.\gradlew.bat -p protocols compileKotlinJvm --no-configuration-cache --console=plain -Dkmp.targets=jvm --max-workers=4
```

Expected: `BUILD SUCCESSFUL` (uses `.worktree-m2` / Nexus for lower layers). If resolve fails, publish missing lower packs to `.worktree-m2` using existing Phase 1 publish scripts, then retry â€” do not re-expand monolith includes.

- [ ] **Step 3: Write gates rows**

Create/update `phase1m-gates.md` with PASS rows for thin root project list, core smoke, protocols smoke.

- [ ] **Step 4: Commit**

```bash
git add phase1m-gates.md
git commit -m "Record phase 1m gates for thin IDK root pack smokes."
```

---

