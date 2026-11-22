#!/usr/bin/env bash
# Checks that every module that is published to Maven Central has every file Central asks for, and that no module that is not
# published has any of them (Phase 14b, D-140).
#
#   scripts/check-central-bundle.sh [--verify] [repository-root]
#
# Run it after `mvn -B -Prelease verify` (the files are in each module's target folder, so nothing needs to be uploaded to check).
# For a module with packaging `jar` it requires the jar, the sources jar, the javadoc jar and the POM, and a `.asc` signature for
# each of the four. For a module with packaging `pom` (the parent and the starter) it requires the POM and its `.asc`: a pom
# module has no jar, and Central asks for sources and javadoc only for a jar.
#
# Which modules are published is read from the poms, not from a list kept here: the modules named in the root pom, minus those
# whose own pom sets <skipPublishing>true</skipPublishing> (sample-app and jpa-admin-ui-preview). host-check is outside the
# reactor and is not looked at. A module that is not published must have NO signature, sources jar or javadoc jar.
#
#   --verify   also run `gpg --verify` on every signature. It uses the keyring that gpg finds (set GNUPGHOME to use another
#              one), and it reads no secret: a signature is checked with the public key only.
#
# Exit status: 0 everything is there; 1 a file is missing or unexpected (each one is printed); 2 the script was used wrongly.
# It prints file names and nothing else: no key, no passphrase, no token.

set -u -o pipefail

verify=false
root=""
for arg in "$@"; do
  case "$arg" in
    --verify) verify=true ;;
    -h|--help) sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) echo "unknown option: $arg" >&2; exit 2 ;;
    *) if [ -n "$root" ]; then echo "only one repository root can be given" >&2; exit 2; fi; root="$arg" ;;
  esac
done
if [ -z "$root" ]; then
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fi
if [ ! -f "$root/pom.xml" ]; then
  echo "no pom.xml in $root" >&2
  exit 2
fi

# The text between two tags of a pom, comments and carriage returns ignored, first match only.
root_version=$(tr -d '\r' < "$root/pom.xml" | sed -n 's|^  <version>\(.*\)</version>.*|\1|p' | head -1)
if [ -z "$root_version" ]; then
  echo "could not read the project version from $root/pom.xml" >&2
  exit 2
fi
case "$root_version" in
  *-SNAPSHOT) echo "the version is $root_version: a release is not a snapshot" >&2; exit 1 ;;
esac

artifact_id_of() {   # a module's own artifactId: the first one after </parent> (the one inside <parent> is the root's)
  tr -d '\r' < "$1" | awk '
    /<\/parent>/ { seen = 1; next }
    seen && /<artifactId>/ { gsub(/.*<artifactId>|<\/artifactId>.*/, ""); print; exit }'
}
root_artifact=$(tr -d '\r' < "$root/pom.xml" | sed -n 's|^  <artifactId>\(.*\)</artifactId>.*|\1|p' | head -1)
packaging_of() {     # the module's packaging, `jar` when it says nothing
  p=$(tr -d '\r' < "$1" | sed -n 's|^  <packaging>\(.*\)</packaging>.*|\1|p' | head -1)
  echo "${p:-jar}"
}
is_unpublished() {   # a module that opts out with <skipPublishing>true</skipPublishing>
  tr -d '\r' < "$1" | grep -q '<skipPublishing>true</skipPublishing>'
}

failures=0
missing() { echo "MISSING:    $1"; failures=$((failures + 1)); }
unexpected() { echo "UNEXPECTED: $1"; failures=$((failures + 1)); }

checked_files=0
check_signed() {     # a file and its .asc must both exist; the .asc must look like a signature
  file="$1"
  checked_files=$((checked_files + 1))
  if [ ! -f "$file" ]; then missing "$file"; return; fi
  if [ ! -s "$file" ]; then missing "$file (it is empty)"; return; fi
  if [ ! -f "$file.asc" ]; then missing "$file.asc"; return; fi
  if ! head -1 "$file.asc" | tr -d '\r' | grep -q '^-----BEGIN PGP SIGNATURE-----$'; then
    missing "$file.asc (it is not an armored signature)"; return
  fi
  if $verify; then
    if ! gpg --batch --quiet --verify "$file.asc" "$file" >/dev/null 2>&1; then
      echo "BAD SIGNATURE: $file.asc does not verify against $file"
      failures=$((failures + 1))
    fi
  fi
}

published=""
unpublished=""
check_module() {     # folder (relative to the root), artifactId, packaging
  folder="$1"; artifact="$2"; packaging="$3"
  target="$root/$folder/target"
  base="$target/$artifact-$root_version"
  if [ ! -d "$target" ]; then missing "$target (build with: mvn -B -Prelease verify)"; return; fi
  check_signed "$base.pom"
  if [ "$packaging" = "jar" ]; then
    check_signed "$base.jar"
    check_signed "$base-sources.jar"
    check_signed "$base-javadoc.jar"
  fi
}

# The root (the parent pom) is published.
check_module "." "$root_artifact" "$(packaging_of "$root/pom.xml")"
published="$root_artifact"

while IFS= read -r module; do
  [ -n "$module" ] || continue
  pom="$root/$module/pom.xml"
  if [ ! -f "$pom" ]; then missing "$pom"; continue; fi
  artifact=$(artifact_id_of "$pom")
  if [ -z "$artifact" ]; then echo "could not read the artifactId of $pom" >&2; failures=$((failures + 1)); continue; fi
  if is_unpublished "$pom"; then
    unpublished="$unpublished $artifact"
    # A module that is not published must leave nothing that could be uploaded: no signature, no sources jar, no javadoc jar.
    for leftover in "$root/$module/target/"*.asc "$root/$module/target/$artifact-$root_version-sources.jar" \
                    "$root/$module/target/$artifact-$root_version-javadoc.jar"; do
      [ -e "$leftover" ] && unexpected "$leftover"
    done
  else
    published="$published $artifact"
    check_module "$module" "$artifact" "$(packaging_of "$pom")"
  fi
done < <(tr -d '\r' < "$root/pom.xml" | sed -n 's|.*<module>\(.*\)</module>.*|\1|p')

echo "version:     $root_version"
echo "published:   $published"
echo "unpublished: ${unpublished:- (none)}"
if [ "$failures" -gt 0 ]; then
  echo "FAILED: $failures problem(s) above."
  exit 1
fi
echo "OK: $checked_files file(s) checked, each with its signature$($verify && echo ', every signature verified')."
