#!/usr/bin/env bash
# Upgrade the existing Ubuntu API services without replacing shared Caddy routes or signing keys.
set -euo pipefail
release=$(realpath "${1:?Usage: sudo bash services/deploy-ubuntu.sh /absolute/release}")
case "$release" in
  /srv/pocket/stage/extras/AnimeComicApi/releases/*) ;;
  *) echo 'Release must be inside the AnimeComicApi releases directory' >&2; exit 1 ;;
esac
[[ $EUID == 0 ]]
data=/srv/pocket/stage/extras
node=/usr/local/bin/node
backup="$data/AnimeComicApi/backups/$(date -u +%Y%m%dT%H%M%S)-$$"
services=(books komiic web)
for name in "${services[@]}"; do
  test -f "$release/services/$name-api/run.mjs"
  systemctl cat "pocket-$name.service" >/dev/null
done
for name in Books Komiic; do
  sudo -u pocket test -r "$data/AnimeComic${name}Api/config/image-signing.key"
done
caddy validate --config /etc/caddy/Caddyfile
cd "$release"
sudo -u pocket "$node" --test services/books-api/*.test.mjs services/komiic-api/*.test.mjs \
  services/web-api/*.test.mjs web/tests/animeko-relay.test.mjs web/tests/bangumi-relay.test.mjs \
  web/tests/oauth-relay.test.mjs web/tests/online-relay.test.mjs web/tests/video-extraction.test.mjs
mkdir -p "$backup"
chmod 700 "$backup"
cp /etc/caddy/Caddyfile "$backup/Caddyfile"
for name in "${services[@]}"; do
  systemctl cat "pocket-$name.service" > "$backup/pocket-$name.service.txt"
  dropin="/etc/systemd/system/pocket-$name.service.d/30-animecomic-api.conf"
  if [[ -f "$dropin" ]]; then cp "$dropin" "$backup/$name.conf"; fi
done
rollback() {
  trap - ERR
  for name in "${services[@]}"; do
    dropin="/etc/systemd/system/pocket-$name.service.d/30-animecomic-api.conf"
    if [[ -f "$backup/$name.conf" ]]; then cp "$backup/$name.conf" "$dropin";
    else rm -f "$dropin"; fi
  done
  systemctl daemon-reload
  systemctl restart pocket-books pocket-komiic pocket-web
  echo "Activation failed; previous service definitions restored. Backup: $backup" >&2
  exit 1
}
trap rollback ERR
for name in "${services[@]}"; do
  directory="/etc/systemd/system/pocket-$name.service.d"
  mkdir -p "$directory"
  cat > "$directory/30-animecomic-api.conf" <<EOF
[Service]
WorkingDirectory=$release/services/$name-api
ExecStart=
ExecStart=$node --max-old-space-size=128 $release/services/$name-api/run.mjs
EOF
done
systemctl daemon-reload
systemctl restart pocket-books pocket-komiic pocket-web
for route in books:4180 komiic:4181 web:4182; do
  name=${route%:*}; port=${route#*:}
  curl --fail --silent --show-error --retry 5 --retry-connrefused --retry-delay 1 \
    "http://127.0.0.1:$port/api/$name/health" >/dev/null
  curl --fail --silent --show-error --max-time 20 "https://43.131.255.74:8443/api/$name/health" >/dev/null
done
trap - ERR
echo "Activated: $release"
echo "Backup: $backup"
