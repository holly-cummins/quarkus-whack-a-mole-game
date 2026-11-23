#!/usr/bin/env bash
# Smoke test for the native build: the native controller launches a grid of native moles,
# every window must appear, and stopping the controller must close them all.
# Needs an X display, e.g. `xvfb-run scripts/smoke-test.sh`, and xwininfo (x11-utils).
set -euo pipefail
cd "$(dirname "$0")/.."

expected=6

controller/target/controller-1.0.0-SNAPSHOT-runner launch --mode NATIVE \
    --screen-width 1280 --screen-height 720 "$expected" &
controller=$!
trap 'kill "$controller" 2>/dev/null || true' EXIT

count=0
for _ in $(seq 1 50); do
    count=$(xwininfo -root -tree | grep -c '"PID [0-9]' || true)
    [ "$count" -ge "$expected" ] && break
    sleep 0.2
done
echo "$count of $expected mole windows appeared"
[ "$count" -ge "$expected" ] || exit 1

moles=$(pgrep -P "$controller" | tr '\n' ' ')
if [ "$(echo $moles | wc -w)" -ne "$expected" ]; then
    echo "Expected $expected mole processes under the controller, found: $moles"
    exit 1
fi
kill "$controller"
wait "$controller" || true
sleep 1
for mole in $moles; do
    if kill -0 "$mole" 2>/dev/null; then
        echo "Mole process $mole was still running after the controller stopped"
        exit 1
    fi
done
echo "Smoke test passed"
