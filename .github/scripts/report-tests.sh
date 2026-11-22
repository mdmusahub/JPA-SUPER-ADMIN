#!/usr/bin/env bash
# What ran, what was skipped and why, read from the surefire reports (Phase 14a, D-128 to D-130).
#
# Prints counts and the skip reasons. It prints no test output and no record value: a skip reason is a fixed sentence. Run it from
# the repository root after `mvn verify`, on CI or on a laptop:   bash .github/scripts/report-tests.sh
#
# It reports, it does not judge: the build already fails when Docker or Chrome is missing on CI (JPA_ADMIN_REQUIRE_ENGINES and
# JPA_ADMIN_REQUIRE_BROWSER are true there), so a skip never hides in a green build.

set -u

# The attributes of every <testsuite ...> line of the given reports, summed: tests, failures, errors, skipped.
sums() {
  grep -ho '<testsuite [^>]*>' "$@" | awk '
    function attribute(line, name,   text) {
      if (match(line, " " name "=\"[0-9]+\"")) {
        text = substr(line, RSTART + length(name) + 3, RLENGTH - length(name) - 4)
        return text + 0
      }
      return 0
    }
    {
      tests += attribute($0, "tests")
      failures += attribute($0, "failures")
      errors += attribute($0, "errors")
      skipped += attribute($0, "skipped")
    }
    END { print tests + 0, failures + 0, errors + 0, skipped + 0 }'
}

for module in jpa-admin-core jpa-admin-autoconfigure jpa-admin-ui-preview sample-app; do
  set -- "$module"/target/surefire-reports/TEST-*.xml
  if [ ! -e "$1" ]; then
    echo "$module: no test reports"
    continue
  fi
  read -r tests failures errors skipped < <(sums "$@")
  echo "$module: $tests tests, $failures failures, $errors errors, $skipped skipped"
done

# A group of test classes by the end of their name, in the modules that have them.
group() {
  label="$1"
  suffix="$2"
  files=""
  for file in sample-app/target/surefire-reports/TEST-*"$suffix".xml jpa-admin-core/target/surefire-reports/TEST-*"$suffix".xml; do
    case "$file" in *FailureNamesEngineTest.xml) continue ;; esac
    if [ -e "$file" ]; then
      files="$files $file"
    fi
  done
  if [ -z "$files" ]; then
    echo "$label: no reports"
    return
  fi
  # shellcheck disable=SC2086
  read -r tests failures errors skipped < <(sums $files)
  echo "$label: $((tests - skipped)) ran, $skipped skipped, $failures failures, $errors errors"
}

group "Real-browser tests" "BrowserTest"
group "Database engine tests (H2, PostgreSQL, MySQL)" "EngineTest"

echo "Skip reasons, with the number of tests that carry each (none is expected on CI):"
reasons=$(grep -rhoE 'Assumption failed: .*' --include='TEST-*.xml' . 2>/dev/null | sort | uniq -c | sort -rn)
if [ -z "$reasons" ]; then
  echo "  none"
else
  echo "$reasons"
fi
exit 0
