#!/usr/bin/env bash
# PlaceHub Lite end-to-end smoke test (spec section 11.3).
# Runs against the gateway after `docker compose up`. Creates fresh users with random emails, so it is re-runnable.
# Seeded data used: the dev admin account (step 15); v3 steps also use the seeded recruiters, jobs and policy and
# restore the policy bands and screening mode they change.
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

# ================================================================================================ v3 (spec 20.8)
# Uses seeded jobs: GSE 12 LPA (-0001, recruiter@), Data Analyst Intern (-0002), Embedded 10 LPA (-0003),
# Senior Data Engineer 20 LPA (-0005, recruiter2@), Quant Developer 30 LPA (-0006). Fresh students only.
GSE=20000000-0000-0000-0000-000000000001
INTERN=20000000-0000-0000-0000-000000000002
EMBEDDED=20000000-0000-0000-0000-000000000003
SENIOR_DE=20000000-0000-0000-0000-000000000005
QUANT=20000000-0000-0000-0000-000000000006
SEEDED_PASSWORD="${SEEDED_PASSWORD:-PlaceHub@123}"

violation_codes() { json '[.details.violations[]?.code] | join(",")'; }
expect_violation() { # code description
  if [ "$CODE" = "422" ] && [[ ",$(violation_codes)," == *",$1,"* ]]; then pass "$2 (422 $1)"
  else fail "$2: expected 422 $1, got $CODE" "$(echo "$BODY" | head -c 300)"; fi
}
set_status() { # token appId status
  req PATCH "/api/applications/$2/status" "$1" "{\"status\":\"$3\"}"
}

RECRUITER1_TOKEN="$(login recruiter@placehub.local "$SEEDED_PASSWORD")"
RECRUITER2_TOKEN="$(login recruiter2@placehub.local "$SEEDED_PASSWORD")"

step v3.1 "Applying without a resume is rejected with RESUME_REQUIRED"
NORESUME_EMAIL="smoke.noresume.$RUN_ID@example.com"
register_student "$NORESUME_EMAIL" 8.60 CSE
NORESUME_TOKEN="$(login "$NORESUME_EMAIL" Smoke12345)"
req POST /api/applications "$NORESUME_TOKEN" "{\"jobId\":\"$GSE\"}"
expect_violation RESUME_REQUIRED "apply to GSE without a resume"

step v3.2 "recruiter2 switches Senior Data Engineer to AUTO_SHORTLIST; a fresh qualifying student is shortlisted by SYSTEM"
req GET "/api/jobs/$SENIOR_DE" "$RECRUITER2_TOKEN"
JOB_BODY="$(echo "$BODY" | jq -c '{title, description, location, employmentType, ctc, ctcLpa, minCgpa, maxBacklogs,
  eligibleBranches, eligibleGraduationYears, deadline, screeningMode: "AUTO_SHORTLIST", autoShortlistMinCgpa: 8.0,
  autoShortlistMaxBacklogs: 0}')"
req PUT "/api/jobs/$SENIOR_DE" "$RECRUITER2_TOKEN" "$JOB_BODY"
[ "$CODE" = "200" ] && [ "$(json '.screeningMode')" = "AUTO_SHORTLIST" ] && pass "screening mode saved (HTTP 200)" \
  || fail "set AUTO_SHORTLIST: HTTP $CODE" "$(echo "$BODY" | head -c 300)"
AUTO_EMAIL="smoke.auto.$RUN_ID@example.com"
register_student "$AUTO_EMAIL" 8.60 CSE
AUTO_TOKEN="$(login "$AUTO_EMAIL" Smoke12345)"
upload_resume "$AUTO_TOKEN"
req POST /api/applications "$AUTO_TOKEN" "{\"jobId\":\"$SENIOR_DE\"}"
AUTO_APP="$(json '.id')"
[ "$CODE" = "201" ] && [ "$(json '.status')" = "SHORTLISTED" ] && pass "unplaced student applies and is SHORTLISTED immediately" \
  || fail "auto-shortlist apply: HTTP $CODE status $(json '.status')" "$(echo "$BODY" | head -c 300)"
req GET "/api/applications/$AUTO_APP/history" "$AUTO_TOKEN"
[ "$(json '[.[] | select(.toStatus == "SHORTLISTED" and .actorType == "SYSTEM")] | length')" = "1" ] \
  && pass "history shows SHORTLISTED by SYSTEM" || fail "history lacks SYSTEM shortlist" "$(echo "$BODY" | head -c 300)"

step v3.3 "Placement flow under policy v1 (block < 1.5x, consent 1.5-2x with lock, allow >= 2x)"
req GET /api/admin/policy "$ADMIN_TOKEN"
ORIGINAL_POLICY="$(echo "$BODY" | jq -c '{bands, appliesToInternships, maxActiveConsents, resumeRequired,
  withdrawOtherApplicationsOnLock, consentText}')"
ORIGINAL_VERSION="$(json '.version')"
# Compared numerically: a seeded version serialises 2.0, a re-saved one 2.
[ "$(json '[.bands[] | "\(.fromMultiplier * 100 | round)-\(if .toMultiplier == null then "inf" else (.toMultiplier * 100 | round) end)-\(.action)-\(.lockOnPlacement)"] | join(" ")')" \
  = "0-150-BLOCK-false 150-200-REQUIRE_CONSENT-true 200-inf-ALLOW-false" ] \
  && pass "active policy v$ORIGINAL_VERSION has the seeded bands" \
  || fail "active policy bands differ from the seeded v1; later expectations may not hold" "$(json '.bands' | tr -d '\n ')"
PLACED_EMAIL="smoke.placed.$RUN_ID@example.com"
register_student "$PLACED_EMAIL" 8.60 CSE
PLACED_ID="$(json '.id')"
PLACED_TOKEN="$(login "$PLACED_EMAIL" Smoke12345)"
upload_resume "$PLACED_TOKEN"
req POST /api/applications "$PLACED_TOKEN" "{\"jobId\":\"$GSE\"}"
expect_code 201 "apply to GSE (12 LPA)"
GSE_APP="$(json '.id')"
set_status "$RECRUITER1_TOKEN" "$GSE_APP" SHORTLISTED
set_status "$RECRUITER1_TOKEN" "$GSE_APP" SELECTED
expect_code 200 "recruiter selects the student for GSE"
req GET /api/applications/me/placement-state "$PLACED_TOKEN"
[ "$(json '.placed')" = "true" ] && [ "$(json '.currentCtc')" = "12" -o "$(json '.currentCtc')" = "12.00" ] && [ "$(json '.locked')" = "false" ] \
  && pass "student is placed at 12 LPA and not locked" || fail "placement state after GSE" "$BODY"
req POST /api/applications "$PLACED_TOKEN" "{\"jobId\":\"$EMBEDDED\"}"
expect_violation POLICY_BLOCKED "Embedded 10 LPA (0.83x)"
req POST /api/applications "$PLACED_TOKEN" "{\"jobId\":\"$SENIOR_DE\"}"
expect_violation CONSENT_REQUIRED "Senior Data Engineer 20 LPA (1.67x) without consent"
req POST /api/applications "$PLACED_TOKEN" "{\"jobId\":\"$SENIOR_DE\",\"consent\":{\"accepted\":true,\"signedName\":\"Smoke Student\"}}"
expect_code 201 "Senior Data Engineer with consent"
SDE_APP="$(json '.id')"
req POST /api/applications "$PLACED_TOKEN" "{\"jobId\":\"$QUANT\"}"
expect_code 201 "Quant Developer 30 LPA (2.5x) allowed"
QUANT_APP="$(json '.id')"
req POST /api/applications "$PLACED_TOKEN" "{\"jobId\":\"$INTERN\"}"
expect_code 201 "Data Analyst Intern unaffected (policy does not apply to internships)"
req GET "/api/applications/$SDE_APP" "$RECRUITER2_TOKEN"
[ "$(json '.status')" = "SELECTED" ] || { [ "$(json '.status')" = "APPLIED" ] && set_status "$RECRUITER2_TOKEN" "$SDE_APP" SHORTLISTED; }
set_status "$RECRUITER2_TOKEN" "$SDE_APP" SELECTED
expect_code 200 "recruiter2 selects the student at 20 LPA (lock band)"
req GET /api/applications/me/placement-state "$PLACED_TOKEN"
[ "$(json '.locked')" = "true" ] && pass "student is locked" || fail "student not locked after lock-band selection" "$BODY"
req GET "/api/applications/$QUANT_APP/history" "$PLACED_TOKEN"
[ "$(json '[.[] | select(.toStatus == "WITHDRAWN" and .actorType == "SYSTEM")] | length')" = "1" ] \
  && pass "Quant application auto-withdrawn by SYSTEM" || fail "Quant not auto-withdrawn" "$(echo "$BODY" | head -c 300)"
req POST /api/applications "$PLACED_TOKEN" "{\"jobId\":\"$JOB_ID\"}"
expect_violation STUDENT_LOCKED "a locked student cannot apply"

step v3.4 "Admin unlocks the student; the action is audited"
req POST "/api/admin/students/$PLACED_ID/unlock" "$ADMIN_TOKEN" '{"reason":"Smoke: offer declined, approved by TPO"}'
[ "$CODE" = "200" ] || [ "$CODE" = "204" ] && pass "unlock (HTTP $CODE)" || fail "unlock: HTTP $CODE" "$BODY"
req GET /api/applications/me/placement-state "$PLACED_TOKEN"
[ "$(json '.locked')" = "false" ] && pass "student is unlocked" || fail "still locked" "$BODY"
req GET '/api/admin/policy/audit?size=50' "$ADMIN_TOKEN"
[ "$(json "[.content[] | select(.action == \"STUDENT_UNLOCKED\" and .studentUserId == \"$PLACED_ID\")] | length")" = "1" ] \
  && pass "audit log has STUDENT_UNLOCKED with the reason" || fail "unlock not audited" "$(echo "$BODY" | head -c 300)"

step v3.5 "New policy version (consent 1.5-2.5x); simulate; earlier snapshots unchanged; then restore"
NEW_POLICY="$(echo "$ORIGINAL_POLICY" | jq -c '.bands = [
  {fromMultiplier: 0, toMultiplier: 1.5, action: "BLOCK", lockOnPlacement: false},
  {fromMultiplier: 1.5, toMultiplier: 2.5, action: "REQUIRE_CONSENT", lockOnPlacement: true},
  {fromMultiplier: 2.5, toMultiplier: null, action: "ALLOW", lockOnPlacement: false}] | .changeNote = "Smoke: widen consent band"')"
req PUT /api/admin/policy "$ADMIN_TOKEN" "$NEW_POLICY"
NEW_VERSION="$(json '.version')"
[ "$CODE" = "200" ] && [ "$NEW_VERSION" = "$((ORIGINAL_VERSION + 1))" ] && pass "policy v$NEW_VERSION is active" \
  || fail "publish new policy: HTTP $CODE" "$(echo "$BODY" | head -c 300)"
req POST /api/admin/policy/simulate "$ADMIN_TOKEN" '{"currentCtc":12,"jobCtc":28,"employmentType":"FULL_TIME","locked":false}'
[ "$(json '.decision')" = "REQUIRE_CONSENT" ] && pass "simulate 12 -> 28 LPA (2.33x) = REQUIRE_CONSENT" || fail "simulate" "$BODY"
req GET "/api/applications/$SDE_APP" "$PLACED_TOKEN"
[ "$(json '.policyDecision')" = "REQUIRE_CONSENT" ] && pass "earlier application keeps its policy snapshot" || fail "snapshot changed" "$BODY"
req GET "/api/admin/consents?studentUserId=$PLACED_ID" "$ADMIN_TOKEN"
[ "$(json "[.content[] | select(.applicationId == \"$SDE_APP\" and .policyVersion == $ORIGINAL_VERSION)] | length")" = "1" ] \
  && pass "consent still records policy v$ORIGINAL_VERSION" || fail "consent version changed" "$(echo "$BODY" | head -c 300)"
req PUT /api/admin/policy "$ADMIN_TOKEN" "$(echo "$ORIGINAL_POLICY" | jq -c '.changeNote = "Smoke: restore seeded bands"')"
expect_code 200 "restore the original bands as a new version"

step v3.6 "Consent CSV export contains the consent row"
CSV="$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/admin/consents/export.csv?studentUserId=$PLACED_ID")"
echo "$CSV" | grep -q "$SDE_APP" && echo "$CSV" | grep -q "Smoke Student" && pass "CSV row for the consent present" \
  || fail "CSV export missing the consent" "$(echo "$CSV" | head -c 300)"

step v3.7 "Login portals reject other roles only after a correct password"
req POST /api/auth/login "" "{\"email\":\"student@placehub.local\",\"password\":\"$SEEDED_PASSWORD\",\"portal\":\"RECRUITER\"}"
expect_code 403 "student via the recruiter portal"
req POST /api/auth/login "" '{"email":"student@placehub.local","password":"wrong-password-1","portal":"RECRUITER"}'
expect_code 401 "wrong password via the recruiter portal stays a generic 401"

step v3.8 "Restore Senior Data Engineer to MANUAL screening (keeps the demo data as seeded)"
req PUT "/api/jobs/$SENIOR_DE" "$RECRUITER2_TOKEN" "$(echo "$JOB_BODY" | jq -c '.screeningMode = "MANUAL" | del(.autoShortlistMinCgpa, .autoShortlistMaxBacklogs)')"
expect_code 200 "screening mode back to MANUAL"

echo
echo "Smoke test finished: $PASSES passed, $FAILS failed."
[ "$FAILS" -eq 0 ] || exit 1
