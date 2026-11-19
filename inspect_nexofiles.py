import os

java_dir = "app/src/main/java"
kt_files = []
for root, _, files in os.walk(java_dir):
    for f in sorted(files):
        if f.endswith(".kt"):
            kt_files.append(os.path.join(root, f))

targets = [
    ("1_TEMAS_EVA", ["SELECTOR DE TEMAS EVA", "Unidad 00"]),
    ("2_TOPBAR_Y_WEB", ["NEXOFILES // HUD", "ABRIR NAVEGADOR WEB", "NexoSimpleWebBrowserDialog"]),
    ("3_VISOR_FOTO_VIDEO", ["100%", "1.0x", "1.0X", "VideoView", "ExoPlayer", "VideoPlayer"]),
    ("4_FILES_LANDSCAPE_BACK", ["+ TAB", "Search here", "HIDDEN:", "BackHandler", "navigateUp"]),
    ("5_VAULT_DRIVE", ["GOOGLE DRIVE ENCRYPTED", "Portable encryption passphrase", "NexoVault_Encrypted", "respaldos"]),
    ("6_OTA_UPDATER", ["CHECK FOR UPDATES", "Descarga completa", "api.github.com/repos"])
]

out_lines = ["=== ARCHIVOS KT DEL PROYECTO ==="] + kt_files
summary = []

for label, keywords in targets:
    out_lines.append(f"\n================ {label} ================")
    hits = 0
    for path in kt_files:
        with open(path, "r", encoding="utf-8", errors="ignore") as fp:
            lines = fp.readlines()
        matched = [i for i, l in enumerate(lines) if any(k in l for k in keywords)]
        if matched:
            ranges = []
            for idx in matched:
                s, e = max(0, idx - 10), min(len(lines), idx + 35)
                if ranges and s <= ranges[-1][1]:
                    ranges[-1] = (ranges[-1][0], max(ranges[-1][1], e))
                else:
                    ranges.append((s, e))
            for s, e in ranges[:3]:
                hits += 1
                out_lines.append(f"\n--- FILE: {path} (L{s+1}-L{e}) ---")
                for ln in range(s, e):
                    out_lines.append(f"{ln+1}: {lines[ln].rstrip()}")
    summary.append(f"  {label}: {hits} bloque(s)")

dest = os.path.expanduser("~/diag.txt")
with open(dest, "w", encoding="utf-8") as fp:
    fp.write("\n".join(out_lines))

print("✅ Diagnóstico listo en ~/diag.txt (sin compilar):")
for s in summary:
    print(s)
print(f"Tamaño: {os.path.getsize(dest) // 1024} KB")
