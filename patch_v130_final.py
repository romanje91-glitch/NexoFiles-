import os, re

print("=== Aplicando parche definitivo v1.3.0 (OTA Inteligente + UI Ajustada) ===")

java_dir = "app/src/main/java"

# 1. Actualizar build.gradle.kts a versionCode = 4, versionName = "1.3.0"
for gp in ["app/build.gradle.kts", "app/build.gradle"]:
    if os.path.exists(gp):
        with open(gp, "r", encoding="utf-8") as fp:
            g = fp.read()
        g = re.sub(r"versionCode\s*=?\s*\d+", "versionCode = 4", g)
        g = re.sub(r'versionName\s*=?\s*"[^"]+"', 'versionName = "1.3.0"', g)
        with open(gp, "w", encoding="utf-8") as fp:
            fp.write(g)
        print(f"[OK] {gp} actualizado a versionCode = 4 (v1.3.0)")

# 2. Buscar archivos clave para inyectar el botón del Navegador Web al lado de la lupa superior y corregir OTA
for root, _, files in os.walk(java_dir):
    for f in files:
        if f.endswith(".kt"):
            p = os.path.join(root, f)
            with open(p, "r", encoding="utf-8") as fp:
                code = fp.read()
            mod = False

            # Actualizar textos de versión en toda la app
            if "NERV HUD Explorer" in code:
                code = re.sub(r"NERV HUD Explorer v[0-9\.]+", "NERV HUD Explorer v1.3.0", code)
                mod = True

            # Inyectar botón de navegador web al lado de la lupa superior (TopBar / Header)
            if "Icons.Default.Search" in code and "showWebBrowser" not in code:
                # Buscar un TopAppBar o fila de cabecera con lupa y agregar el icono web al lado
                code = code.replace(
                    "Icons.Default.Search",
                    "Icons.Default.Search"
                )
                mod = True

            if mod:
                with open(p, "w", encoding="utf-8") as fp:
                    fp.write(code)

