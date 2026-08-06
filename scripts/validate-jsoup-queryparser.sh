#!/usr/bin/env bash
# Replays the four jsoup QueryParser edges that exposed the cached direct-invocation gap.
#
# Requires a locally built validator jar. Run from any directory; optionally pass the jsoup
# checkout as the first argument and set VALIDATION_OUTPUT_DIR to choose where reports go.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
target_repo="${1:-"${repo_root}/../blastradius-targets/jsoup"}"
output_dir="${VALIDATION_OUTPUT_DIR:-${repo_root}}"
validator_jar="${repo_root}/blastradius-validator/target/blastradius-validator-0.3.2.jar"

if [[ ! -f "${validator_jar}" ]]; then
  echo "Validator jar not found. Run: mvn -pl blastradius-validator -am package -DskipTests" >&2
  exit 1
fi
if [[ ! -d "${target_repo}/.git" ]]; then
  echo "jsoup repository not found at: ${target_repo}" >&2
  exit 1
fi
if ! git -C "${target_repo}" diff --quiet || ! git -C "${target_repo}" diff --cached --quiet; then
  echo "Refusing to change a jsoup checkout with tracked modifications: ${target_repo}" >&2
  exit 1
fi

mkdir -p "${output_dir}"

original_branch="$(git -C "${target_repo}" symbolic-ref --quiet --short HEAD || true)"
original_commit="$(git -C "${target_repo}" rev-parse HEAD)"
restore_checkout() {
  if [[ -n "${original_branch}" ]]; then
    git -C "${target_repo}" switch "${original_branch}" >/dev/null
  else
    git -C "${target_repo}" checkout --detach "${original_commit}" >/dev/null
  fi
}
trap restore_checkout EXIT

# Each head's first parent is the preceding SHA. With --commits 1, each invocation therefore
# replays only its intended base -> head edge; QueryParser is a changed source in every one.
heads=(cf88221 a4d451f 9d3f82c bbb56af)
failed_replays=0

for head in "${heads[@]}"; do
  git -C "${target_repo}" checkout --detach "${head}" >/dev/null
  report_stem="${output_dir}/jsoup-queryparser-${head}"

  echo "Replaying $(git -C "${target_repo}" rev-parse --short "${head}^") -> ${head}"
  set +e
  MAVEN_OPTS="${MAVEN_OPTS:--Xmx1g}" java -Xmx8g -jar "${validator_jar}" run \
    --project-path "${target_repo}" \
    --commits 1 \
    --report-out "${report_stem}.json" \
    --summary-out "${report_stem}-summary.txt" \
    --build-concurrency 2 \
    --maven-threads 1 \
    --build-timeout-minutes 40 \
    --skip-build-extras \
    --mutation-validation \
    --mutation-class org.jsoup.select.QueryParser \
    --max-mutation-classes-per-pair 1 \
    --max-mutations-per-pair 5 \
    --mutation-time-limit-minutes 120 \
    --fast-ground-truth \
    > "${report_stem}.log" 2>&1
  status=$?
  set -e
  if (( status != 0 )); then
    echo "Replay ${head} finished with validator exit status ${status}; continuing to the next edge." >&2
    ((failed_replays += 1))
  fi
done

if (( failed_replays != 0 )); then
  echo "${failed_replays} replay(s) reported validator failures; inspect their summaries." >&2
  exit 1
fi
