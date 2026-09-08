#!/usr/bin/env bash
# A green build is not evidence that tests ran.
#
# The iOS project learned this the hard way: when a suite crashes, xcodebuild
# still prints ✔ and the only visible difference is a smaller test count. The
# Gradle equivalent is a module whose test task is skipped, filtered to nothing,
# or silently produces no result XML at all — `BUILD SUCCESSFUL` either way.
#
# So the count is asserted, not assumed. Raise MINIMUM as suites are added; it
# is meant to be a floor that has to be edited deliberately.
#
# `find` rather than `**`: macOS ships bash 3.2, which has no globstar, and a
# check that only runs on CI is a check that cannot be trusted before pushing.
set -euo pipefail

MINIMUM=${MINIMUM:-233}
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

total=0
while IFS= read -r f; do
  n=$(sed -n 's/.*<testsuite [^>]*tests="\([0-9]*\)".*/\1/p' "$f" | head -1)
  [ -n "${n:-}" ] && total=$((total + n))
done < <(find "$ROOT" -path '*/build/test-results/*' -name '*.xml' -type f)

echo "test cases found: $total (minimum $MINIMUM)"
if [ "$total" -lt "$MINIMUM" ]; then
  echo "FAIL: fewer test cases than expected. A suite did not run." >&2
  exit 1
fi
