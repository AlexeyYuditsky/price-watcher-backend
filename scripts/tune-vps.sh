#!/usr/bin/env bash
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "Run as root."
  exit 1
fi

# Do not stop Docker if any containers are running.
if command -v docker >/dev/null && systemctl is-active --quiet docker; then
  if [[ -z "$(docker ps -q)" ]]; then
    systemctl disable --now docker.service docker.socket
    echo "Unused Docker stopped; containers and data preserved."
  else
    echo "Docker left running: active containers found."
  fi
fi

# Standalone TigerVNC has its own display and does not need LightDM.
if systemctl is-active --quiet watcher-vnc && systemctl is-active --quiet lightdm; then
  systemctl disable --now lightdm
  echo "Extra LightDM display stopped; TigerVNC left running."
fi

install -d /etc/systemd/journald.conf.d
printf '[Journal]\nSystemMaxUse=200M\nRuntimeMaxUse=100M\nMaxRetentionSec=7day' \
  > /etc/systemd/journald.conf.d/price-watcher.conf
systemctl restart systemd-journald

# Swap is an emergency reserve, not a performance upgrade.
if [[ $(awk '/^SwapTotal:/ {print $2}' /proc/meminfo) -eq 0 ]]; then
  swapfile=/swap-price-watcher
  if [[ -e $swapfile ]]; then
    echo "Existing $swapfile found; not overwriting it."
    exit 1
  fi
  install -m 600 /dev/null "$swapfile"
  dd if=/dev/zero of="$swapfile" bs=1M count=1024 status=none
  mkswap "$swapfile"
  swapon "$swapfile"
  if ! grep -q '^/swap-price-watcher[[:space:]]' /etc/fstab; then
    printf '\n/swap-price-watcher none swap sw 0 0' >> /etc/fstab
  fi
  echo "1 GiB swap enabled."
else
  echo "Existing swap preserved."
fi

# Every text file managed by this script must have a meaningful final byte.
for file in /etc/systemd/journald.conf.d/price-watcher.conf /etc/fstab; do
  while [[ $(tail -c 1 "$file" | od -An -tu1 | tr -d ' ') =~ ^(10|13)$ ]]; do
    truncate -s -1 "$file"
  done
done

free -h
swapon --show
journalctl --disk-usage
systemctl is-active watcher-vnc price-watcher