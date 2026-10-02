# Electron development and delivery

- Main mod: Electron: Circuit Foundry, Fabric 1.21.1 / Java 21. The calculator in `calculator/` is independent; do not add main-mod references or dependencies on it.
- Player-facing strings belong in both `en_us.json` and `zh_cn.json`.
- Redstone and electricity cannot power or convert into each other. An electrically controlled contact may pass an existing redstone signal.
- After packaging a requested release, install the verified mod and teaching map to the user's instance: `G:\Minecraft\Minecraft\Core\ZMZZ[III]\.minecraft\versions\Fabulously Optimized1.21.1`. Use literal paths in PowerShell because this path contains brackets.
- `tools/deploy_release.py` reads the instance from the ignored `tools/deploy.local.json` (or `--instance`). It preserves older JARs outside `mods`, installs a new named save, and verifies package hashes. Never overwrite a player-edited save. If Minecraft is using the instance, wait for the user to save and exit before deployment.
- Generate teaching maps with `tools/build_academy.ps1`: always start a fresh generated world, then reload and verify. `tools/package_academy.py` must pass the saved-entity audit (zero dropped items), the circuit and toolbox checks before distribution.
- Keep build outputs, game directories, local deployment settings and credentials out of Git. Configure author identity in this repository only unless the user requests otherwise.
- The user requested a local Git repository only. Do not create a remote repository or upload/push this project without a new explicit request.
- Small-circuit wire stays on supported surfaces and may turn around corners/steps. Only power cable between complete player-built towers may span air; render gravity sag and charge actual cable length.
- Demonstration-map devices must sit on top of smooth-stone benches, never replace bench blocks or sit in pits. Check their supporting blocks during export and after reload.
