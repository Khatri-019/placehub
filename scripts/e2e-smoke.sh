#!/usr/bin/env bash
# PlaceHub Lite end-to-end smoke test (spec section 11.3).
# Runs against the gateway after `docker compose up`. Creates fresh users with random emails, so it is re-runnable.
# Seeded data used: the dev admin account only (step 15).
# Requirements: bash, curl, jq. On Windows run it from Git Bash or WSL.
#
#   ./scripts/e2e-smoke.sh                  # gateway at http://localhost:8080
#   GATEWAY_URL=http://host:8080 ./scripts/e2e-smoke.sh
set -uo pipefail

BASE="${GATEWAY_URL:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@placehub.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-PlaceHub@123}"
WAIT_SECONDS="${WAIT_SECONDS:-240}"
NOTIFY_TIMEOUT=15

for tool in curl jq; do
  command -v "$tool" >/dev/null 2>&1 || { echo "ERROR: '$tool' is required but not installed." >&2; exit 2; }
done

TMP_BODY="$(mktemp)"
trap 'rm -f "$TMP_BODY" "${RESUME_FILE:-}"' EXIT
PASSES=0
FAILS=0
CODE=""
BODY=""
RUN_ID="$(date +%s)-$RANDOM"

pass() { PASSES=$((PASSES + 1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
fail() { FAILS=$((FAILS + 1)); printf '  \033[31mFAIL\033[0m %s\n' "$1"; [ -n "${2:-}" ] && printf '       %s\n' "$2"; }
step() { printf '\n[%s] %s\n' "$1" "$2"; }

# req METHOD PATH [TOKEN] [JSON_BODY] -> sets CODE and BODY
req() {
  local method="$1" path="$2" token="${3:-}" data="${4:-}"
  local args=(-s -o "$TMP_BODY" -w '%{http_code}' -X "$method" "$BASE$path" -H 'Accept: application/json'
              -H "X-Correlation-Id: smoke-$RUN_ID")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' --data "$data")
  CODE="$(curl "${args[@]}" 2>/dev/null || echo 000)"
  BODY="$(cat "$TMP_BODY" 2>/dev/null || true)"
}

expect_code() { # expected description
  if [ "$CODE" = "$1" ]; then pass "$2 (HTTP $CODE)"; else fail "$2: expected HTTP $1, got $CODE" "$(echo "$BODY" | head -c 300)"; fi
}

json() { echo "$BODY" | jq -r "$1" 2>/dev/null; }

future_iso() { date -u -d "+$1 days" '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || date -u -v+"$1"d '+%Y-%m-%dT%H:%M:%SZ'; }

register_student() { # email cgpa branch
  req POST /api/auth/register/student "" "{\"fullName\":\"Smoke Student\",\"email\":\"$1\",\"password\":\"Smoke12345\",
    \"collegeName\":\"Smoke Institute\",\"branch\":\"$3\",\"graduationYear\":2027,\"cgpa\":$2,\"backlogCount\":0}"
}

RESUME_FILE="$(mktemp)"
printf '%%PDF-1.4\n%% PlaceHub smoke resume\ntrailer<</Root 1 0 R>>\n%%%%EOF\n' > "$RESUME_FILE"

RESUME_ARG="$RESUME_FILE"
# Git Bash on Windows: native curl cannot open MSYS paths inside -F, so pass a mixed-style path.
command -v cygpath >/dev/null 2>&1 && RESUME_ARG="$(cygpath -m "$RESUME_FILE")"

upload_resume() { # token -> sets CODE and BODY
  CODE="$(curl -s -o "$TMP_BODY" -w '%{http_code}' -X POST "$BASE/api/users/me/resume" \
    -H "Authorization: Bearer $1" -H "X-Correlation-Id: smoke-$RUN_ID" \
    -F "file=@$RESUME_ARG;type=application/pdf;filename=smoke-resume.pdf" 2>/dev/null || echo 000)"
  BODY="$(cat "$TMP_BODY" 2>/dev/null || true)"
}

register_recruiter() { # email
  req POST /api/auth/register/recruiter "" "{\"fullName\":\"Smoke Recruiter\",\"email\":\"$1\",\"password\":\"Smoke12345\",
    \"companyName\":\"Smoke Corp $RUN_ID\",\"designation\":\"Hiring Manager\"}"
}

login() { # email password -> token on stdout (empty on failure)
  req POST /api/auth/login "" "{\"email\":\"$1\",\"password\":\"$2\"}"
  [ "$CODE" = "200" ] && json '.accessToken' || echo ""
}

# poll_notification TOKEN TYPE APPLICATION_ID -> 0 when found within NOTIFY_TIMEOUT seconds
poll_notification() {
  local token="$1" type="$2" app="$3" i
  for ((i = 0; i < NOTIFY_TIMEOUT; i++)); do
    req GET '/api/notifications?size=50' "$token"
    if [ "$CODE" = "200" ] && [ "$(json "[.content[] | select(.type == \"$2\" and .relatedApplicationId == \"$app\")] | length")" -ge 1 ] 2>/dev/null; then
      POLL_SECONDS=$i
      return 0
    fi
    sleep 1
  done
  return 1
}

# ---------------------------------------------------------------------------------------------- wait for the stack
echo "PlaceHub Lite smoke test against $BASE (run $RUN_ID)"
echo "Waiting up to ${WAIT_SECONDS}s for the gateway and a successful admin login..."
ready=0
for ((t = 0; t < WAIT_SECONDS; t += 3)); do
  if curl -fs "$BASE/actuator/health" >/dev/null 2>&1; then
    ADMIN_TOKEN="$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
    if [ -n "$ADMIN_TOKEN" ] && [ "$ADMIN_TOKEN" != "null" ]; then ready=1; break; fi
  fi
  sleep 3
done
if [ "$ready" != 1 ]; then
  echo "ERROR: gateway not ready or admin login failed after ${WAIT_SECONDS}s (last HTTP $CODE)." >&2
  exit 1
fi
echo "Stack is ready."

STUDENT_EMAIL="smoke.student.$RUN_ID@example.com"
WEAK_EMAIL="smoke.weak.$RUN_ID@example.com"
RECRUITER_EMAIL="smoke.recruiter.$RUN_ID@example.com"
OTHER_RECRUITER_EMAIL="smoke.other.$RUN_ID@example.com"
JOB_TITLE="Smoke Backend Engineer $RUN_ID"

step 1 "Student registers and logs in"
register_student "$STUDENT_EMAIL" 8.50 CSE
expect_code 201 "student registration"
STUDENT_ID="$(json '.id')"
STUDENT_TOKEN="$(login "$STUDENT_EMAIL" Smoke12345)"
[ -n "$STUDENT_TOKEN" ] && pass "student login returns a token" || fail "student login" "$BODY"
upload_resume "$STUDENT_TOKEN"
expect_code 201 "student uploads a PDF resume (v3: resumes are required to apply by default)"

step 2 "Recruiter registers and logs in"
register_recruiter "$RECRUITER_EMAIL"
expect_code 201 "recruiter registration"
RECRUITER_TOKEN="$(login "$RECRUITER_EMAIL" Smoke12345)"
[ -n "$RECRUITER_TOKEN" ] && pass "recruiter login returns a token" || fail "recruiter login" "$BODY"

step 3 "Recruiter creates a company and creates + publishes a job (min CGPA 7.0, CSE/ISE)"
req POST /api/companies "$RECRUITER_TOKEN" "{\"name\":\"Smoke Corp $RUN_ID\",\"industry\":\"Software\"}"
expect_code 201 "create company"
req POST /api/jobs "$RECRUITER_TOKEN" "{\"title\":\"$JOB_TITLE\",\"description\":\"Smoke test job\",\"location\":\"Pune\",
  \"employmentType\":\"FULL_TIME\",\"ctcLpa\":11.0,\"minCgpa\":7.0,\"maxBacklogs\":0,\"eligibleBranches\":[\"CSE\",\"ISE\"],
  \"eligibleGraduationYears\":[],\"deadline\":\"$(future_iso 7)\"}"
expect_code 201 "create job (draft)"
JOB_ID="$(json '.id')"
req PATCH "/api/jobs/$JOB_ID/publish" "$RECRUITER_TOKEN"
expect_code 200 "publish job"
[ "$(json '.status')" = "PUBLISHED" ] && pass "job status is PUBLISHED" || fail "job status after publish" "$BODY"

step 4 "Student browses jobs and sees the job"
req GET "/api/jobs?q=$(echo "$RUN_ID" | sed 's/-/%2D/g')&size=50" "$STUDENT_TOKEN"
if [ "$CODE" = "200" ] && [ "$(json "[.content[] | select(.id == \"$JOB_ID\")] | length")" = "1" ]; then
  pass "published job visible in browse results"
else
  fail "job not found in browse results (HTTP $CODE)" "$(echo "$BODY" | head -c 300)"
fi

step 5 "Eligible student applies"
req POST /api/applications "$STUDENT_TOKEN" "{\"jobId\":\"$JOB_ID\"}"
expect_code 201 "apply"
APP_ID="$(json '.id')"

step 6 "Same student applies again"
req POST /api/applications "$STUDENT_TOKEN" "{\"jobId\":\"$JOB_ID\"}"
expect_code 409 "duplicate application rejected"

step 7 "Ineligible student (CGPA 6.0) applies"
register_student "$WEAK_EMAIL" 6.00 CSE
expect_code 201 "second student registration"
WEAK_ID="$(json '.id')"
WEAK_TOKEN="$(login "$WEAK_EMAIL" Smoke12345)"
req POST /api/applications "$WEAK_TOKEN" "{\"jobId\":\"$JOB_ID\"}"
expect_code 422 "ineligible application rejected"
REASONS="$(json '.details.reasons | length')"
if [ "${REASONS:-0}" -ge 1 ] 2>/dev/null; then pass "422 carries reasons: $(json '.details.reasons | join("; ")')"; else fail "422 without details.reasons" "$BODY"; fi

step 8 "Student gets an APPLICATION_CREATED notification within ${NOTIFY_TIMEOUT}s"
if poll_notification "$STUDENT_TOKEN" APPLICATION_CREATED "$APP_ID"; then
  pass "APPLICATION_CREATED notification arrived after ~${POLL_SECONDS}s"
else
  fail "no APPLICATION_CREATED notification within ${NOTIFY_TIMEOUT}s" "$(echo "$BODY" | head -c 300)"
fi

step 9 "Recruiter lists applicants for the job"
req GET "/api/applications/job/$JOB_ID" "$RECRUITER_TOKEN"
if [ "$CODE" = "200" ] && [ "$(json "[.content[] | select(.id == \"$APP_ID\" and .studentUserId == \"$STUDENT_ID\")] | length")" = "1" ]; then
  pass "applicant list contains the student"
else
  fail "applicant list missing the student (HTTP $CODE)" "$(echo "$BODY" | head -c 300)"
fi

step 10 "Recruiter shortlists the application"
req PATCH "/api/applications/$APP_ID/status" "$RECRUITER_TOKEN" '{"status":"SHORTLISTED","note":"Strong fundamentals"}'
expect_code 200 "shortlist"

step 11 "Application status is SHORTLISTED"
req GET "/api/applications/$APP_ID" "$STUDENT_TOKEN"
[ "$CODE" = "200" ] && [ "$(json '.status')" = "SHORTLISTED" ] && pass "student sees SHORTLISTED" \
  || fail "unexpected application state (HTTP $CODE)" "$BODY"

step 12 "Student gets a status-change notification within ${NOTIFY_TIMEOUT}s"
if poll_notification "$STUDENT_TOKEN" APPLICATION_STATUS_CHANGED "$APP_ID"; then
  pass "APPLICATION_STATUS_CHANGED notification arrived after ~${POLL_SECONDS}s"
else
  fail "no status-change notification within ${NOTIFY_TIMEOUT}s" "$(echo "$BODY" | head -c 300)"
fi

step 13 "Second student cannot see the first student's application or notifications"
req GET "/api/applications/$APP_ID" "$WEAK_TOKEN"
expect_code 404 "other student's application is hidden"
req GET '/api/notifications?size=50' "$WEAK_TOKEN"
if [ "$CODE" = "200" ] \
   && [ "$(json "[.content[] | select(.relatedApplicationId == \"$APP_ID\" or .recipientUserId != \"$WEAK_ID\")] | length")" = "0" ]; then
  pass "second student's notifications contain none of the first student's"
else
  fail "notification leak or error (HTTP $CODE)" "$(echo "$BODY" | head -c 300)"
fi

step 14 "A second recruiter cannot edit the job or update the application"
register_recruiter "$OTHER_RECRUITER_EMAIL"
expect_code 201 "second recruiter registration"
OTHER_TOKEN="$(login "$OTHER_RECRUITER_EMAIL" Smoke12345)"
req PUT "/api/jobs/$JOB_ID" "$OTHER_TOKEN" "{\"title\":\"Hijacked\",\"description\":\"x\",\"location\":\"x\",
  \"employmentType\":\"FULL_TIME\",\"eligibleBranches\":[],\"eligibleGraduationYears\":[],\"deadline\":\"$(future_iso 7)\"}"
expect_code 403 "foreign job edit forbidden"
req PATCH "/api/applications/$APP_ID/status" "$OTHER_TOKEN" '{"status":"REJECTED"}'
expect_code 403 "foreign application update forbidden"

step 15 "Seeded admin can use admin endpoints; a student cannot"
for path in /api/admin/users /api/admin/jobs /api/admin/applications; do
  req GET "$path" "$ADMIN_TOKEN"
  expect_code 200 "admin GET $path"
  req GET "$path" "$STUDENT_TOKEN"
  expect_code 403 "student GET $path"
done

step 16 "Internal endpoints are not reachable through the gateway"
req GET "/internal/users/$STUDENT_ID"
expect_code 404 "GET /internal/users/<id> via gateway"

step "extra" "Student withdraws; recruiter is notified (Definition of Done)"
req PATCH "/api/applications/$APP_ID/withdraw" "$STUDENT_TOKEN"
expect_code 200 "withdraw"
req PATCH "/api/applications/$APP_ID/withdraw" "$STUDENT_TOKEN"
expect_code 409 "second withdraw rejected (terminal status)"
if poll_notification "$RECRUITER_TOKEN" APPLICATION_WITHDRAWN "$APP_ID"; then
  pass "recruiter received APPLICATION_WITHDRAWN after ~${POLL_SECONDS}s"
else
  fail "recruiter not notified of withdrawal within ${NOTIFY_TIMEOUT}s" "$(echo "$BODY" | head -c 300)"
fi

echo
echo "Smoke test finished: $PASSES passed, $FAILS failed."
[ "$FAILS" -eq 0 ] || exit 1
