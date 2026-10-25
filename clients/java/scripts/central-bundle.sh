#!/usr/bin/env bash
# central-bundle.sh <staged repository> <version> <bundle.zip>: the Maven Central bundle of com.voplica:voplica-api,
# made from the Maven layout Gradle stages (publishMavenJavaPublicationToStagingRepository). Its five files - the jar,
# the sources and javadoc jars, the POM and the Gradle module - each keep the .md5 / .sha1 / .sha256 / .sha512 Gradle
# wrote (checked here) and get an ASCII-armoured detached signature (.asc, verified before it is kept); they are zipped
# in the repository layout the Central Portal takes. The staged layout itself is left as it is.
# The key: MAVEN_GPG_PRIVATE_KEY (an ASCII-armoured secret key) with MAVEN_GPG_PASSPHRASE, imported into a GnuPG home of
# its own that is removed on exit; neither reaches a command line or the log, which shows the key's fingerprint only.
set -euo pipefail

die() { echo "::error::$*" >&2; exit 1; }
[ "$#" -eq 3 ] || die "usage: central-bundle.sh <staged repository> <version> <bundle.zip>"
staged=$1 version=$2 bundle=$3
component="com/voplica/voplica-api/$version"
base="voplica-api-$version"
files=("$base.jar" "$base-sources.jar" "$base-javadoc.jar" "$base.pom" "$base.module")
sums=(md5 sha1 sha256 sha512)

[ -d "$staged/$component" ] || die "$staged holds no $component"
[ -n "${MAVEN_GPG_PRIVATE_KEY:-}" ] || die "MAVEN_GPG_PRIVATE_KEY is empty: the secret is not set, nothing is signed"
[ -n "${MAVEN_GPG_PASSPHRASE:-}" ] || die "MAVEN_GPG_PASSPHRASE is empty: the secret is not set, nothing is signed"

# The staged files are these five and their checksums, nothing else: a file the bundle would carry unsigned is refused.
for path in "$staged/$component"/*; do
  name=${path##*/}
  for file in "${files[@]}"; do
    [ "$name" = "$file" ] && continue 2
    for sum in "${sums[@]}"; do [ "$name" = "$file.$sum" ] && continue 3; done
  done
  die "the staged layout holds $name, which is not one of the bundle's files"
done

work=$(mktemp -d)
export GNUPGHOME="$work/gnupg"
cleanup() {
  gpgconf --kill gpg-agent >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT
mkdir -m 700 "$GNUPGHOME"

gpg --batch --quiet --pinentry-mode loopback --passphrase-fd 3 --import 3<<<"$MAVEN_GPG_PASSPHRASE" <<<"$MAVEN_GPG_PRIVATE_KEY" ||
  die "MAVEN_GPG_PRIVATE_KEY did not import: it must be an ASCII-armoured secret key (gpg --armor --export-secret-keys <key>)"
fingerprint=$(gpg --batch --with-colons --list-secret-keys | awk -F: '$1 == "fpr" { print $10; exit }')
[ -n "$fingerprint" ] || die "MAVEN_GPG_PRIVATE_KEY holds no secret key"
echo "Signing with the key $fingerprint"

mkdir -p "$work/bundle/$component"
for file in "${files[@]}"; do
  path="$work/bundle/$component/$file"
  [ -f "$staged/$component/$file" ] || die "the staged layout has no $file"
  cp "$staged/$component/$file" "$path"
  for sum in "${sums[@]}"; do
    [ -f "$staged/$component/$file.$sum" ] || die "the staged layout has no $file.$sum"
    cp "$staged/$component/$file.$sum" "$path.$sum"
    [ "$(cat "$path.$sum")" = "$("${sum}sum" "$path" | cut -d' ' -f1)" ] || die "$file.$sum is not the $sum of $file"
  done
  gpg --batch --yes --quiet --pinentry-mode loopback --passphrase-fd 3 --local-user "$fingerprint" \
    --armor --detach-sign --output "$path.asc" "$path" 3<<<"$MAVEN_GPG_PASSPHRASE" ||
    die "$file could not be signed: is MAVEN_GPG_PASSPHRASE the key's passphrase?"
  # VALIDSIG's first field is the key that made the signature (a signing subkey when the key has one); its last is the
  # primary key's fingerprint (GnuPG's doc/DETAILS), the one printed above.
  verified=$(gpg --batch --status-fd 1 --verify "$path.asc" "$path" 2>/dev/null) || verified=''
  awk -v key="$fingerprint" '$1 == "[GNUPG:]" && $2 == "VALIDSIG" && $NF == key { found = 1 } END { exit !found }' <<<"$verified" ||
    die "the signature of $file does not verify with the key $fingerprint"
done

mkdir -p "$(dirname "$bundle")"
rm -f "$bundle"
jar --create --no-manifest --file "$bundle" -C "$work/bundle" com
entries=$(jar --list --file "$bundle" | grep -v '/$' | sort)
printf '%s\n' "$entries"
count=$(printf '%s\n' "$entries" | grep -c .)
[ "$count" -eq $((${#files[@]} * (${#sums[@]} + 2))) ] || die "the bundle holds $count files, not $((${#files[@]} * (${#sums[@]} + 2)))"
echo "The bundle of com.voplica:voplica-api:$version: $count files, every one of the ${#files[@]} signed by $fingerprint"
