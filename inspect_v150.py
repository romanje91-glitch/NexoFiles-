import os

ROOT = "app/src/main/java/com/example"
out = []

def dump_full(path, label):
    if not os.path.exists(path):
        out.append(f"\n=== MISSING: {label} ===")
        return
    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()
    out.append(f"\n=== {label} ({len(lines)}L) ===")
    for i, l in enumerate(lines):
        out.append(f"{i+1}: {l.rstrip()}")

def dump_matches(rel, keywords, before=8, after=50, max_blocks=6):
    p = os.path.join(ROOT, rel)
    if not os.path.exists(p):
        out.append(f"\n=== MISSING: {rel} ===")
        return
    with open(p, "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()
    hits = [i for i, l in enumerate(lines) if any(k in l for k in keywords)]
    ranges = []
    for idx in hits:
        s, e = max(0, idx - before), min(len(lines), idx + after)
        if ranges and s <= ranges[-1][1]:
            ranges[-1] = (ranges[-1][0], max(ranges[-1][1], e))
        else:
            ranges.append((s, e))
    out.append(f"\n=== {rel} (TOTAL {len(lines)}L) ===")
    for s, e in ranges[:max_blocks]:
        out.append(f"--- L{s+1}-L{e} ---")
        for i in range(s, e):
            out.append(f"{i+1}: {lines[i].rstrip()}")

dump_full("app/build.gradle.kts", "app/build.gradle.kts")
dump_matches("data/repo/GoogleDriveRepository.kt", ["Vault", "vault", "createFolder", "upload", "NexoVault", "PATCH", "parents"], before=8, after=55, max_blocks=6)
dump_matches("ui/viewmodel/ToolsAndNetworkViewModel.kt", ["addFileToVault", "addUriToVault", "deleteVaultItem", "refreshVaultDriveBackups", "backupVaultToDrive", "restoreVaultDriveBackup", "VaultUiState", "ensureValidDriveToken"], before=6, after=55, max_blocks=6)
dump_matches("ui/screens/SettingsScreen.kt", ["VAULT", "showPinDialog", "masterPin", "runProtectedAction", "onSetMasterPin", "CHANGE VAULT SECURITY", "NERV HUD Explorer"], before=8, after=55, max_blocks=6)
dump_full(os.path.join(ROOT, "security/BiometricSecurityManager.kt"), "BiometricSecurityManager.kt")
dump_matches("ui/screens/FilesScreen.kt", ["selectedPaths", "MoreVert", "MoreHoriz", "bottomBar", "combinedClickable", "onLongClick", "FileRow"], before=8, after=45, max_blocks=6)
dump_matches("ui/screens/VaultScreen.kt", ["DRIVE .NEXO_VAULT", "Portable encryption", "onAddUriToVault", "BichromaticCard", "selectedVaultIds", "onRestoreDriveBackup"], before=8, after=45, max_blocks=5)
dump_matches("ui/screens/HomeScreen.kt", ["greetingHello", "NERV //", "STORAGE", "quickToolsTitle", "NexoGlobeIcon"], before=6, after=40, max_blocks=4)
dump_matches("ui/i18n/AppStrings.kt", ["NERV //", "NERV HUD Explorer", "greetingHello"], before=2, after=5, max_blocks=6)

dest = os.path.expanduser("~/diag_v2.txt")
with open(dest, "w", encoding="utf-8") as f:
    f.write("\n".join(out))
print(f"✅ Listo: ~/diag_v2.txt ({os.path.getsize(dest) // 1024} KB)")
