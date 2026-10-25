#!/usr/bin/env bash
# central-portal.sh: com.voplica:voplica-api on Maven Central, through the Central Portal's publisher API
# (https://central.sonatype.org/publish/publish-portal-api/), for .github/workflows/java.yml:
#   central-portal.sh published <version>
#       writes present=true to $GITHUB_OUTPUT (stdout without it) when the version is on Maven Central already - synced
#       to repo1.maven.org, or published by the Portal and not synced yet - and present=false otherwise
#   central-portal.sh upload <bundle.zip> <version>
#       uploads the signed bundle (central-bundle.sh) for automatic publishing, then waits until the Portal has published
#       it; a version that exists already is a notice, not a failure
# The Portal user token comes from MAVEN_CENTRAL_USERNAME and MAVEN_CENTRAL_PASSWORD; it is masked in the log and never
# reaches a command line. CENTRAL_PORTAL_URL, CENTRAL_REPOSITORY_URL, CENTRAL_POLL_SECONDS and CENTRAL_WAIT_SECONDS
# override the defaults below.
set -euo pipefail

namespace=com.voplica
name=voplica-api
portal=${CENTRAL_PORTAL_URL:-https://central.sonatype.com/api/v1/publisher}
repository=${CENTRAL_REPOSITORY_URL:-https://repo1.maven.org/maven2}
poll=${CENTRAL_POLL_SECONDS:-15}
wait=${CENTRAL_WAIT_SECONDS:-2700}
deployments=https://central.sonatype.com/publishing/deployments

die() { echo "::error::$*" >&2; exit 1; }

# The Portal's bearer value, base64 of username:password; masked before any use.
bearer=
authorize() {
  [ -n "${MAVEN_CENTRAL_USERNAME:-}" ] || die "MAVEN_CENTRAL_USERNAME is empty: the secret is not set, nothing is published"
  [ -n "${MAVEN_CENTRAL_PASSWORD:-}" ] || die "MAVEN_CENTRAL_PASSWORD is empty: the secret is not set, nothing is published"
  bearer=$(printf '%s:%s' "$MAVEN_CENTRAL_USERNAME" "$MAVEN_CENTRAL_PASSWORD" | base64 | tr -d '\n')
  echo "::add-mask::$bearer"
}

# call <method> <path?query> [curl arguments...]: one Portal request; its HTTP status in $status (000 when there was no
# answer) and its body in $body. A refused token stops the script.
status='' body=''
call() {
  local method=$1 path=$2 out
  shift 2
  out=$(mktemp)
  status=$(curl --silent --show-error --request "$method" --output "$out" --write-out '%{http_code}' \
    --header @<(printf 'Authorization: Bearer %s\n' "$bearer") "$@" "$portal/$path") || status=000
  body=$(cat "$out")
  rm -f "$out"
  case $status in
    401 | 403) die "the Central Portal refused the token (HTTP $status): MAVEN_CENTRAL_USERNAME and MAVEN_CENTRAL_PASSWORD must be a Portal user token (central.sonatype.com, View Account, Generate User Token) with the namespace $namespace" ;;
  esac
}

output() {
  if [ -n "${GITHUB_OUTPUT:-}" ]; then echo "$1" >> "$GITHUB_OUTPUT"; else echo "$1"; fi
}

published() {
  local version=$1 code
  code=$(curl --silent --output /dev/null --write-out '%{http_code}' --location \
    "$repository/${namespace//.//}/$name/$version/$name-$version.pom") || code=000
  if [ "$code" = 200 ]; then
    echo "::notice::$namespace:$name:$version is on Maven Central already"
    output present=true
    return
  fi
  authorize
  call GET "published?namespace=$namespace&name=$name&version=$version"
  if [ "$status" = 200 ] && [ "$(jq -r '.published' <<<"$body")" = true ]; then
    echo "::notice::$namespace:$name:$version is published by the Central Portal already (Maven Central has not synced it yet)"
    output present=true
    return
  fi
  if [ "$status" != 200 ]; then
    echo "::warning::the Central Portal could not say whether $version is published (HTTP $status): the upload decides"
  fi
  echo "$namespace:$name:$version is not on Maven Central yet"
  output present=false
}

upload() {
  local bundle=$1 version=$2 deployment state='' last='' errors deadline misses=0
  [ -f "$bundle" ] || die "no bundle at $bundle"
  authorize
  call POST "upload?name=$name-$version&publishingType=AUTOMATIC" --form "bundle=@$bundle;type=application/octet-stream"
  [ "${status:0:1}" = 2 ] || die "the Central Portal refused the bundle (HTTP $status): $body"
  deployment=$(tr -d '"[:space:]' <<<"$body")
  [[ $deployment =~ ^[0-9A-Fa-f-]{36}$ ]] || die "the Central Portal answered the upload with \"$body\", not a deployment id"
  echo "Uploaded ${bundle##*/} as the deployment $deployment ($deployments)"
  deadline=$((SECONDS + wait))
  while :; do
    call POST "status?id=$deployment"
    if [ "$status" != 200 ]; then
      # The Portal goes on with an uploaded deployment without this job: a passing failure to answer is asked again.
      misses=$((misses + 1))
      if [ "$misses" -ge 5 ] || [ "$SECONDS" -ge "$deadline" ]; then
        die "the status of the deployment $deployment is unavailable (HTTP $status, $misses times in a row): $body ($deployments)"
      fi
      echo "The status of the deployment $deployment is unavailable (HTTP $status): asking again"
      sleep "$poll"
      continue
    fi
    misses=0
    state=$(jq -r '.deploymentState // "none"' <<<"$body" 2>/dev/null) ||
      die "the status of the deployment $deployment is not JSON: $body"
    [ "$state" = "$last" ] || echo "The deployment $deployment is $state"
    last=$state
    case $state in
      PUBLISHED)
        echo "::notice::$namespace:$name:$version is published to Maven Central (the deployment $deployment)"
        return ;;
      FAILED)
        errors=$(jq -c '.errors // {}' <<<"$body")
        if grep -qi 'already exist' <<<"$errors"; then
          echo "::notice::$namespace:$name:$version is on Maven Central already: $errors"
          return
        fi
        die "the deployment $deployment failed: $errors" ;;
      PENDING | VALIDATING | VALIDATED | PUBLISHING) ;;
      *) die "the deployment $deployment is in a state this script does not know, \"$state\": $body" ;;
    esac
    if [ "$SECONDS" -ge "$deadline" ]; then
      case $state in
        VALIDATED | PUBLISHING)
          echo "::notice::the deployment $deployment passed validation and is $state after ${wait} s: the Portal publishes it to Maven Central without this job ($deployments)"
          return ;;
      esac
      die "the deployment $deployment is still $state after ${wait} s ($deployments)"
    fi
    sleep "$poll"
  done
}

case ${1:-} in
  published) [ "$#" -eq 2 ] || die "usage: central-portal.sh published <version>"; published "$2" ;;
  upload) [ "$#" -eq 3 ] || die "usage: central-portal.sh upload <bundle.zip> <version>"; upload "$2" "$3" ;;
  *) die "usage: central-portal.sh published <version> | upload <bundle.zip> <version>" ;;
esac
