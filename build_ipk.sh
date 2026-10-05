#!/bin/bash
set -euo pipefail

echo "=========================================================="
echo "    Mluona IPTV - LG webOS IPK Builder (ares-package)"
echo "=========================================================="

if ! command -v ares-package &> /dev/null; then
    echo "[*] Installing @webos-tools/cli..."
    npm install -g @webos-tools/cli
fi

node --check ipk_package/webos/js/app.js
node -e "JSON.parse(require('fs').readFileSync('ipk_package/webos/appinfo.json','utf8'))"

mkdir -p dist
# --no-minify: ares-package's minifier chokes on modern JS and can corrupt vendor libs
ares-package --no-minify ipk_package/webos -o dist/

echo "[✓] Built package:"
ls -lh dist/com.mluona.iptv_*.ipk
