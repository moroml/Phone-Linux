#!/data/data/com.termux/files/usr/bin/bash
# Termux の中で実行する。Ubuntu(proot) を入れ、その中に公式の Claude Code を入れて、
# Termux から `claude` で起動できるようにする。何度実行しても同じ状態になる。
# 使い方: bash setup.sh
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"

pkg upgrade -y
pkg install -y proot-distro git

# Android 用の Claude Code は配布されていないため、glibc の Ubuntu の中で linux-arm64 版を動かす
[ -d "$PREFIX/var/lib/proot-distro/installed-rootfs/ubuntu" ] || proot-distro install ubuntu
proot-distro login ubuntu -- bash -c '
  set -eo pipefail
  apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq curl ca-certificates git ripgrep python3 python-is-python3 python3-yaml >/dev/null
  [ -x /root/.local/bin/claude ] || curl -fsSL https://claude.ai/install.sh | bash
  grep -q .local/bin ~/.bashrc || echo "export PATH=\$HOME/.local/bin:\$PATH" >> ~/.bashrc
'

install -m 755 "$here/claude" "$PREFIX/bin/claude"
mkdir -p ~/.termux ~/projects
[ -f ~/.termux/termux.properties ] && cp ~/.termux/termux.properties ~/.termux/termux.properties.bak
cp "$here/termux.properties" ~/.termux/termux.properties
termux-reload-settings || true

echo "完了。次に: termux-setup-storage(任意) → cd ~/projects && claude でログイン"
