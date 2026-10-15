#!/usr/bin/env bash
# Greps the tree for forbidden patterns. Run from the repo root.
set -euo pipefail

cd "$(dirname "$0")/../.."

failed=0
total=0

# fail <rule> <message> <matches>: reports a rule when <matches> is not empty.
fail() {
  total=$((total + 1))
  if [ -n "$3" ]; then
    failed=$((failed + 1))
    echo "FAIL $1: $2"
    echo "$3" | sed 's/^/  /'
  fi
}

kt() { grep -rn --include='*.kt' -E "$1" "${@:2}" 2>/dev/null || true; }

fail core-no-server-api "Keep :core free of Bukkit, Paper, Adventure and Brigadier; move the code to :paper." \
  "$(kt 'org\.bukkit|io\.papermc|net\.kyori|com\.mojang' core/src)"

fail scheduler-only-in-platform "Schedule through Scheduler; call Bukkit.getScheduler( only in PaperScheduler.kt." \
  "$(kt 'Bukkit\.getScheduler\(' core/src paper/src | grep -v '^paper/src/main/kotlin/net/craftgame/metro/platform/PaperScheduler.kt:' || true)"

fail chunk-tickets-only-in-chunktickets "Hold chunks through ChunkTickets; do not call the plugin ticket API directly." \
  "$(kt '(add|remove)PluginChunkTicket' core/src paper/src | grep -v '^paper/src/main/kotlin/net/craftgame/metro/route/ChunkTickets.kt:' || true)"

fail no-suppress "Fix the finding instead of using @Suppress." \
  "$(kt '@Suppress' core/src paper/src)"

fail detekt-baseline-frozen "Fix new detekt findings; the baselines may only hold TooManyFunctions for EditManager." \
  "$(grep -no '<ID>[^<]*</ID>' config/detekt/baseline*.xml | grep -v '<ID>TooManyFunctions:EditManager.kt:EditManager</ID>' || true)"

# The dash is built from its bytes so this file does not contain it.
dash=$(printf '\342\200\224')
fail no-em-dash "Replace the em dash with a comma, colon or full stop." \
  "$(grep -rn --include='*.kt' --include='*.md' --include='lang.yml' --include='config.yml' --include='plugin.yml' \
    --exclude-dir=build --exclude-dir=.git --exclude-dir=.claude --exclude-dir=.gradle \
    -F "$dash" . 2>/dev/null || true)"

if [ "$failed" -gt 0 ]; then
  echo "$failed of $total rules failed"
  exit 1
fi
echo "All $total rules passed"
