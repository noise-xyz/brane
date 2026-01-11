#!/usr/bin/env bash
# Orchestrator Code Review v7.2 - Layered Hallucination Defense
#
# Key improvements over v6:
#   1. Layer 1: Enumerated file/line indices (prevents invented paths)
#   2. Layer 2: Semantic anchoring with code quote verification
#   3. Layer 3: Bidirectional validation via re-localization (different model)
#   4. Comprehensive hallucination metrics tracking
#
# v7.2 fixes (production hardening):
#   - File-based stat tracking (fixes subshell counter loss)
#   - Optimized jq patterns (O(n) instead of O(n²) memory)
#   - Checkpoint/partial result persistence on interrupts
#   - Improved rate limit detection (multi-strategy)
#
# Defense Pipeline:
#   Discovery (Haiku) → Index Validation → Quote Verification → Re-localization (Sonnet) → Verification (Sonnet) → Opus Review
#
# Requirements:
#   - bash 4.2+ (macOS: brew install bash)
#   - jq: JSON parsing (brew install jq)
#   - bc: Cost calculations (usually pre-installed)
#   - flock: File locking (standard on Linux; macOS: brew install util-linux)
#
# Usage: ./.claude/scripts/code_review_orchestrator_v7.sh [base_branch] [--config <file>]

set -euo pipefail

# Script directory for sourcing libs
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Early defaults for logging (before init_defaults)
DEBUG="${DEBUG:-false}"
LOG_FORMAT="${LOG_FORMAT:-human}"
FLOCK_AVAILABLE=true
TIMEOUT_CMD=""  # Set in check_prerequisites to timeout or gtimeout

# Initialize tracking variables
TOTAL_COST=0
OPUS_OVERRIDES=0
NUM_BATCHES=0
DISCOVERY_COUNT=0
VERIFIED_COUNT=0
REJECTED_COUNT=0
TASK_COUNT=0
CHANGED_FILE_COUNT=0
ALL_RESULTS=""
ALL_DISCOVERIES=""
WORK_DIR=""

# Initialize arrays (compatible with bash 3.2+)
BACKGROUND_PIDS=()
PIDS=()
FILE_INDEX=()
NUM_FILES=0

#######################################
# Exit Codes
#######################################
readonly EXIT_SUCCESS=0
readonly EXIT_FINDINGS_FOUND=1
readonly EXIT_ERROR=2
readonly EXIT_BUDGET_EXCEEDED=3
readonly EXIT_PARTIAL_FAILURE=4
readonly EXIT_PREREQ_MISSING=5

#######################################
# Prerequisite Checks
#######################################
check_prerequisites() {
    # Require bash 4.2+ for declare -g and associative arrays
    if [[ "${BASH_VERSINFO[0]}" -lt 4 ]] || { [[ "${BASH_VERSINFO[0]}" -eq 4 ]] && [[ "${BASH_VERSINFO[1]}" -lt 2 ]]; }; then
        echo "ERROR: bash 4.2+ required (found ${BASH_VERSION})" >&2
        echo "       macOS: brew install bash && /opt/homebrew/bin/bash $0 $*" >&2
        exit $EXIT_PREREQ_MISSING
    fi

    if ! command -v jq &>/dev/null; then
        echo "ERROR: jq is required but not installed" >&2
        exit $EXIT_PREREQ_MISSING
    fi

    if ! command -v claude &>/dev/null; then
        echo "ERROR: claude CLI not found in PATH" >&2
        exit $EXIT_PREREQ_MISSING
    fi

    if ! command -v bc &>/dev/null; then
        echo "ERROR: bc is required for cost calculations but not installed" >&2
        exit $EXIT_PREREQ_MISSING
    fi

    # Find gtimeout (macOS) or native timeout (Linux)
    if command -v gtimeout &>/dev/null; then
        TIMEOUT_CMD="gtimeout"
    elif command -v timeout &>/dev/null; then
        TIMEOUT_CMD="timeout"
    else
        echo "ERROR: gtimeout/timeout command is required but not installed" >&2
        echo "       macOS: brew install coreutils" >&2
        echo "       Linux: coreutils package provides this command" >&2
        exit $EXIT_PREREQ_MISSING
    fi

    if ! command -v flock &>/dev/null; then
        echo "WARN: flock not found - file locking disabled (race conditions possible)" >&2
        FLOCK_AVAILABLE=false
    else
        FLOCK_AVAILABLE=true
    fi

    # Source hallucination defense library
    if [[ -f "$SCRIPT_DIR/lib/hallucination_defense.sh" ]]; then
        source "$SCRIPT_DIR/lib/hallucination_defense.sh"
    else
        echo "ERROR: hallucination_defense.sh not found in $SCRIPT_DIR/lib/" >&2
        exit $EXIT_PREREQ_MISSING
    fi

    # Source calibration library (optional)
    if [[ -f "$SCRIPT_DIR/lib/calibration.sh" ]]; then
        source "$SCRIPT_DIR/lib/calibration.sh"
    fi
}

#######################################
# Input Validation
#######################################
validate_git_ref() {
    local ref="$1"
    local ref_type="$2"

    # Check for empty
    if [[ -z "$ref" ]]; then
        log_error "$ref_type cannot be empty"
        exit $EXIT_ERROR
    fi

    # Validate against shell injection and invalid git ref characters
    # Git refs: alphanumeric, underscore, dash, dot, slash (no consecutive dots, no @{, etc.)
    if ! [[ "$ref" =~ ^[a-zA-Z0-9][a-zA-Z0-9._/-]*$ ]]; then
        log_error "Invalid $ref_type: '$ref' - contains invalid characters"
        exit $EXIT_ERROR
    fi

    # Block dangerous patterns
    if [[ "$ref" == *".."* ]] || [[ "$ref" == *"@{"* ]] || [[ "$ref" == *"~"* ]] || [[ "$ref" == *"^"* ]]; then
        log_error "Invalid $ref_type: '$ref' - contains potentially dangerous patterns"
        exit $EXIT_ERROR
    fi

    # Block shell metacharacters explicitly
    if [[ "$ref" == *";"* ]] || [[ "$ref" == *"&"* ]] || [[ "$ref" == *"|"* ]] || \
       [[ "$ref" == *">"* ]] || [[ "$ref" == *"<"* ]] || [[ "$ref" == *'`'* ]] || \
       [[ "$ref" == *'$'* ]] || [[ "$ref" == *"("* ]] || [[ "$ref" == *")"* ]]; then
        log_error "Invalid $ref_type: '$ref' - contains shell metacharacters"
        exit $EXIT_ERROR
    fi

    return 0
}

#######################################
# Default Configuration
#######################################
init_defaults() {
    BASE_BRANCH="${1:-main}"
    validate_git_ref "$BASE_BRANCH" "base branch"
    SPEC_DIR="spec"
    REVIEW_MD="$SPEC_DIR/code_review.md"
    TASKS_TXT="$SPEC_DIR/tasks.txt"
    WORK_DIR="$SPEC_DIR/.orchestrator_v7"

    # Parallelism
    MAX_PARALLEL_DISCOVERY=4
    MAX_PARALLEL_VERIFY=4
    MAX_PARALLEL_RELOC=6

    # Limits
    MAX_FINDINGS_TO_VERIFY=30
    MAX_FINDINGS_PER_FILE=5
    MAX_LINES_PER_CHUNK=400
    CHUNK_OVERLAP_LINES=30
    MAX_LINES_FOR_RELOC=500

    # Cost controls
    MAX_COST_PER_CALL=0.50
    MAX_TOTAL_BUDGET=5.00
    TOTAL_COST=0

    # Retry settings
    MAX_RETRIES=3
    INITIAL_BACKOFF=2
    API_TIMEOUT=120  # seconds per API call

    # Models (use different models for discovery vs reloc to avoid shared bias)
    MODEL_DISCOVERY="haiku"
    MODEL_RELOC="sonnet"  # Different from discovery for independent validation
    MODEL_VERIFY="sonnet"
    MODEL_CORRELATE="sonnet"
    MODEL_OPUS="opus"

    # Opus escalation
    ENABLE_OPUS_REVIEW=true
    MIN_T1T2_FOR_OPUS=2

    # Hallucination defense settings
    ENABLE_QUOTE_VERIFICATION=true
    ENABLE_RELOCALIZATION=true
    RELOC_TOLERANCE=3

    # Self-consistency settings (multi-run discovery with voting)
    ENABLE_SELF_CONSISTENCY=true
    SELF_CONSISTENCY_RUNS=3          # Number of discovery runs
    SELF_CONSISTENCY_THRESHOLD=0.5   # Minimum agreement ratio (0.5 = 50% = majority)
}

#######################################
# Config File Support
#######################################

# Validate integer config value
validate_int_config() {
    local name="$1"
    local value="$2"
    local min="${3:-1}"
    local max="${4:-1000}"

    if ! [[ "$value" =~ ^[0-9]+$ ]]; then
        log_error "Invalid config: $name must be an integer (got: '$value')"
        exit $EXIT_ERROR
    fi

    if [[ "$value" -lt "$min" ]] || [[ "$value" -gt "$max" ]]; then
        log_error "Invalid config: $name must be between $min and $max (got: $value)"
        exit $EXIT_ERROR
    fi
}

# Validate float config value
validate_float_config() {
    local name="$1"
    local value="$2"

    if ! [[ "$value" =~ ^[0-9]+\.?[0-9]*$ ]]; then
        log_error "Invalid config: $name must be a number (got: '$value')"
        exit $EXIT_ERROR
    fi
}

# Validate boolean config value
validate_bool_config() {
    local name="$1"
    local value="$2"

    if [[ "$value" != "true" ]] && [[ "$value" != "false" ]]; then
        log_error "Invalid config: $name must be 'true' or 'false' (got: '$value')"
        exit $EXIT_ERROR
    fi
}

# Validate model config value
validate_model_config() {
    local name="$1"
    local value="$2"

    case "$value" in
        haiku|sonnet|opus) ;;
        *)
            log_error "Invalid config: $name must be 'haiku', 'sonnet', or 'opus' (got: '$value')"
            exit $EXIT_ERROR
            ;;
    esac
}

load_config() {
    local config_file="$1"
    if [[ -f "$config_file" ]]; then
        log_info "Loading config from $config_file"

        # Validate JSON syntax first
        if ! jq empty "$config_file" 2>/dev/null; then
            log_error "Invalid JSON in config file: $config_file"
            exit $EXIT_ERROR
        fi

        # Load values
        MAX_PARALLEL_DISCOVERY=$(jq -r '.max_parallel_discovery // 4' "$config_file")
        MAX_PARALLEL_VERIFY=$(jq -r '.max_parallel_verify // 4' "$config_file")
        MAX_PARALLEL_RELOC=$(jq -r '.max_parallel_reloc // 6' "$config_file")
        MAX_FINDINGS_TO_VERIFY=$(jq -r '.max_findings_to_verify // 30' "$config_file")
        MAX_TOTAL_BUDGET=$(jq -r '.max_total_budget_usd // 5.0' "$config_file")
        MODEL_DISCOVERY=$(jq -r '.model_discovery // "haiku"' "$config_file")
        MODEL_RELOC=$(jq -r '.model_reloc // "sonnet"' "$config_file")
        MODEL_VERIFY=$(jq -r '.model_verify // "sonnet"' "$config_file")
        ENABLE_OPUS_REVIEW=$(jq -r 'if .enable_opus_review == null then true else .enable_opus_review end' "$config_file")
        ENABLE_QUOTE_VERIFICATION=$(jq -r 'if .enable_quote_verification == null then true else .enable_quote_verification end' "$config_file")
        ENABLE_RELOCALIZATION=$(jq -r 'if .enable_relocalization == null then true else .enable_relocalization end' "$config_file")
        RELOC_TOLERANCE=$(jq -r '.reloc_tolerance // 3' "$config_file")
        API_TIMEOUT=$(jq -r '.api_timeout // 120' "$config_file")
        ENABLE_SELF_CONSISTENCY=$(jq -r 'if .enable_self_consistency == null then true else .enable_self_consistency end' "$config_file")
        SELF_CONSISTENCY_RUNS=$(jq -r '.self_consistency_runs // 3' "$config_file")
        SELF_CONSISTENCY_THRESHOLD=$(jq -r '.self_consistency_threshold // 0.5' "$config_file")

        # Validate all values
        validate_int_config "max_parallel_discovery" "$MAX_PARALLEL_DISCOVERY" 1 20
        validate_int_config "max_parallel_verify" "$MAX_PARALLEL_VERIFY" 1 20
        validate_int_config "max_parallel_reloc" "$MAX_PARALLEL_RELOC" 1 20
        validate_int_config "max_findings_to_verify" "$MAX_FINDINGS_TO_VERIFY" 1 500
        validate_float_config "max_total_budget_usd" "$MAX_TOTAL_BUDGET"
        validate_model_config "model_discovery" "$MODEL_DISCOVERY"
        validate_model_config "model_reloc" "$MODEL_RELOC"
        validate_model_config "model_verify" "$MODEL_VERIFY"
        validate_bool_config "enable_opus_review" "$ENABLE_OPUS_REVIEW"
        validate_bool_config "enable_quote_verification" "$ENABLE_QUOTE_VERIFICATION"
        validate_bool_config "enable_relocalization" "$ENABLE_RELOCALIZATION"
        validate_int_config "reloc_tolerance" "$RELOC_TOLERANCE" 0 20
        validate_int_config "api_timeout" "$API_TIMEOUT" 10 600
        validate_bool_config "enable_self_consistency" "$ENABLE_SELF_CONSISTENCY"
        validate_int_config "self_consistency_runs" "$SELF_CONSISTENCY_RUNS" 1 5
        validate_float_config "self_consistency_threshold" "$SELF_CONSISTENCY_THRESHOLD"

        log_info "Config validation passed"
    fi
}

#######################################
# Structured Logging
#######################################
log_json() {
    local level="$1"
    local msg="$2"
    local extra="${3:-}"
    local ts
    ts=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    if [[ -n "$extra" ]]; then
        printf '{"ts":"%s","level":"%s","msg":"%s",%s}\n' "$ts" "$level" "$msg" "$extra" >&2
    else
        printf '{"ts":"%s","level":"%s","msg":"%s"}\n' "$ts" "$level" "$msg" >&2
    fi
}

log_info() {
    if [[ "$LOG_FORMAT" == "json" ]]; then
        log_json "INFO" "$*"
    else
        echo "[INFO] $*"
    fi
}

log_warn() {
    if [[ "$LOG_FORMAT" == "json" ]]; then
        log_json "WARN" "$*"
    else
        echo "[WARN] $*" >&2
    fi
}

log_error() {
    if [[ "$LOG_FORMAT" == "json" ]]; then
        log_json "ERROR" "$*"
    else
        echo "[ERROR] $*" >&2
    fi
}

log_debug() {
    if [[ "$DEBUG" == "true" ]]; then
        if [[ "$LOG_FORMAT" == "json" ]]; then
            log_json "DEBUG" "$*"
        else
            echo "[DEBUG] $*" >&2
        fi
    fi
}

#######################################
# Checkpointing and Partial Result Persistence
#######################################

# Current phase tracking for checkpoints
CURRENT_PHASE=""
CHECKPOINT_FILE=""

# Save checkpoint after each major phase
save_checkpoint() {
    local phase="$1"
    CURRENT_PHASE="$phase"

    if [[ -z "${WORK_DIR:-}" ]] || [[ ! -d "$WORK_DIR" ]]; then
        return
    fi

    CHECKPOINT_FILE="$WORK_DIR/checkpoint.json"

    # Save current state
    local discoveries_count=0
    local results_count=0

    if [[ -f "$ALL_DISCOVERIES" ]]; then
        discoveries_count=$(jq 'length' "$ALL_DISCOVERIES" 2>/dev/null || echo "0")
    fi
    if [[ -f "$ALL_RESULTS" ]]; then
        results_count=$(jq 'length' "$ALL_RESULTS" 2>/dev/null || echo "0")
    fi

    cat > "$CHECKPOINT_FILE" << EOF
{
    "phase": "$phase",
    "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
    "discoveries_count": $discoveries_count,
    "results_count": $results_count,
    "total_cost": $(calculate_total_cost)
}
EOF

    log_debug "Checkpoint saved: $phase (discoveries=$discoveries_count, results=$results_count)"
}

# Save partial results to spec directory for recovery
save_partial_results() {
    local reason="$1"
    local partial_dir="${SPEC_DIR:-spec}/partial_review_$(date +%Y%m%d_%H%M%S)"

    mkdir -p "$partial_dir"

    # Copy all available results
    if [[ -f "$ALL_DISCOVERIES" ]]; then
        cp "$ALL_DISCOVERIES" "$partial_dir/discoveries.json" 2>/dev/null || true
    fi
    if [[ -f "$ALL_RESULTS" ]]; then
        cp "$ALL_RESULTS" "$partial_dir/results.json" 2>/dev/null || true
    fi
    if [[ -f "$CHECKPOINT_FILE" ]]; then
        cp "$CHECKPOINT_FILE" "$partial_dir/checkpoint.json" 2>/dev/null || true
    fi

    # Copy any intermediate files that might be useful
    if [[ -d "$WORK_DIR/discovery" ]]; then
        cp "$WORK_DIR/discovery"/*.json "$partial_dir/" 2>/dev/null || true
    fi
    if [[ -d "$WORK_DIR/verify" ]]; then
        cp "$WORK_DIR/verify"/*.json "$partial_dir/" 2>/dev/null || true
    fi

    # Write summary
    cat > "$partial_dir/README.txt" << EOF
Partial Review Results
======================
Reason: $reason
Phase: ${CURRENT_PHASE:-unknown}
Timestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
Cost: \$$(calculate_total_cost)

Files:
- discoveries.json: Findings discovered (may be incomplete)
- results.json: Verified results (may be incomplete)
- checkpoint.json: Last checkpoint state
- batch_*_result.json: Raw discovery batch results
- *_result.json: Verification results

To resume or analyze:
  jq '.[] | select(.severity == "T1" or .severity == "T2")' results.json
EOF

    log_warn "Partial results saved to: $partial_dir"
    echo "$partial_dir"
}

#######################################
# Cleanup Handler
#######################################
cleanup() {
    local exit_code=$?

    # Kill background processes
    set +u
    local pid
    for pid in "${BACKGROUND_PIDS[@]}"; do
        if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
            kill "$pid" 2>/dev/null || true
        fi
    done
    set -u

    # Clean up lock files
    if [[ -n "${WORK_DIR:-}" ]] && [[ -d "$WORK_DIR" ]]; then
        find "$WORK_DIR" -name "*.lock" -delete 2>/dev/null || true
        find "$WORK_DIR" -name "*.lockdir" -type d -delete 2>/dev/null || true
    fi

    # Save partial results on error or interrupt
    if [[ $exit_code -ne 0 ]]; then
        local reason="unknown"
        case $exit_code in
            $EXIT_BUDGET_EXCEEDED) reason="budget_exceeded" ;;
            $EXIT_PARTIAL_FAILURE) reason="partial_failure" ;;
            130) reason="interrupted_sigint" ;;
            143) reason="terminated_sigterm" ;;
            124) reason="timeout" ;;
            *) reason="error_code_$exit_code" ;;
        esac

        # Only save if we have meaningful work to preserve
        if [[ -n "${ALL_DISCOVERIES:-}" ]] && [[ -f "$ALL_DISCOVERIES" ]]; then
            local count
            count=$(jq 'length' "$ALL_DISCOVERIES" 2>/dev/null || echo "0")
            if [[ "$count" -gt 0 ]]; then
                save_partial_results "$reason"
            fi
        fi
    fi

    exit $exit_code
}

trap cleanup EXIT INT TERM

#######################################
# File Locking Utilities
#######################################

# Portable lock acquisition using mkdir (atomic on POSIX)
# Args: $1 = lockfile path
# Returns: 0 on success, 1 on failure after timeout
acquire_lock() {
    local lockdir="$1.lockdir"
    local max_attempts=50  # 5 seconds max wait (50 * 0.1s)
    local attempt=0

    while [[ $attempt -lt $max_attempts ]]; do
        if mkdir "$lockdir" 2>/dev/null; then
            return 0
        fi
        sleep 0.1
        attempt=$((attempt + 1))
    done

    log_warn "Failed to acquire lock: $lockdir after ${max_attempts} attempts"
    return 1
}

# Release lock
# Args: $1 = lockfile path
release_lock() {
    local lockdir="$1.lockdir"
    rmdir "$lockdir" 2>/dev/null || true
}

append_safe() {
    local file="$1"
    local content="$2"

    if [[ "$FLOCK_AVAILABLE" == "true" ]]; then
        # Use flock if available (faster)
        (
            flock -x 200
            echo "$content" >> "$file"
        ) 200>"${file}.lock"
    else
        # Portable fallback using mkdir-based locking
        if acquire_lock "$file"; then
            echo "$content" >> "$file"
            release_lock "$file"
        else
            # Fallback to unlocked write (better than failing)
            log_warn "Lock failed, writing without lock to $file"
            echo "$content" >> "$file"
        fi
    fi
}

#######################################
# Cost Tracking (File-Based for Concurrency)
#######################################
COST_FILE=""

init_cost_tracking() {
    COST_FILE="$WORK_DIR/costs.log"
    : > "$COST_FILE"  # Create/truncate
}

track_cost() {
    local response="$1"
    local call_cost
    call_cost=$(echo "$response" | jq -r '.total_cost_usd // 0')

    # Append cost atomically to file (safe for concurrent writes)
    append_safe "$COST_FILE" "$call_cost"

    log_debug "Cost: \$$call_cost added to tracking"
}

# Calculate total cost from file (call from main process only)
calculate_total_cost() {
    if [[ ! -f "$COST_FILE" ]]; then
        echo "0"
        return
    fi
    # Sum all costs in the file
    awk '{sum += $1} END {printf "%.4f", sum}' "$COST_FILE" 2>/dev/null || echo "0"
}

# Check budget (call periodically from main process)
check_budget() {
    TOTAL_COST=$(calculate_total_cost)

    if (( $(echo "$TOTAL_COST > $MAX_TOTAL_BUDGET" | bc -l) )); then
        log_error "Budget exceeded: \$$TOTAL_COST > \$$MAX_TOTAL_BUDGET"
        exit $EXIT_BUDGET_EXCEEDED
    fi
}

#######################################
# JSON Schemas
#######################################
readonly DISCOVERY_SCHEMA_V7='{
  "type": "object",
  "properties": {
    "findings": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "file_index": {"type": "integer", "minimum": 1},
          "line_index": {"type": "integer", "minimum": 1},
          "code_quote": {"type": "string", "minLength": 5},
          "severity": {"type": "string", "enum": ["T1", "T2", "T3", "T4"]},
          "category": {"type": "string"},
          "description": {"type": "string", "minLength": 10}
        },
        "required": ["file_index", "line_index", "code_quote", "severity", "category", "description"]
      }
    },
    "clean_files": {
      "type": "array",
      "items": {"type": "integer"}
    }
  },
  "required": ["findings"]
}'

readonly RELOC_SCHEMA='{
  "type": "object",
  "properties": {
    "found": {"type": "boolean"},
    "line": {"type": ["integer", "null"]},
    "confidence": {"type": "string", "enum": ["HIGH", "MEDIUM", "LOW"]},
    "reasoning": {"type": "string"},
    "issue_type": {"type": "string", "description": "Brief categorization: null-deref, unclosed-resource, race-condition, etc."},
    "code_snippet": {"type": "string", "description": "The problematic code at the reported line"}
  },
  "required": ["found", "confidence", "reasoning"]
}'

readonly VERIFY_SCHEMA='{
  "type": "object",
  "properties": {
    "verdicts": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "file": {"type": "string"},
          "line": {"type": "integer"},
          "verdict": {"type": "string", "enum": ["CONFIRMED", "REJECTED", "DOWNGRADE_T3", "DOWNGRADE_T4"]},
          "severity": {"type": "string", "enum": ["T1", "T2", "T3", "T4"]},
          "title": {"type": "string"},
          "evidence": {"type": "string"},
          "fix": {"type": "string"}
        },
        "required": ["file", "line", "verdict"]
      }
    },
    "learnings": {
      "type": "array",
      "items": {"type": "string"}
    }
  },
  "required": ["verdicts"]
}'

readonly OPUS_SCHEMA='{
  "type": "object",
  "properties": {
    "reviews": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "original_file": {"type": "string"},
          "original_line": {"type": "integer"},
          "verdict": {"type": "string", "enum": ["AGREE", "DISAGREE", "UPGRADE_T1", "DOWNGRADE_T3"]},
          "reason": {"type": "string"}
        },
        "required": ["original_file", "original_line", "verdict", "reason"]
      }
    },
    "summary": {
      "type": "object",
      "properties": {
        "accuracy_ratio": {"type": "string"},
        "false_positives": {"type": "array", "items": {"type": "string"}}
      }
    }
  },
  "required": ["reviews"]
}'

readonly CORRELATE_SCHEMA='{
  "type": "object",
  "properties": {
    "executive_summary": {"type": "string"},
    "findings_by_tier": {
      "type": "object",
      "properties": {
        "T1": {"type": "array", "items": {"type": "object"}},
        "T2": {"type": "array", "items": {"type": "object"}},
        "T3": {"type": "array", "items": {"type": "object"}},
        "T4": {"type": "array", "items": {"type": "object"}}
      }
    },
    "recommended_actions": {
      "type": "array",
      "items": {"type": "string"}
    }
  },
  "required": ["executive_summary", "findings_by_tier", "recommended_actions"]
}'

#######################################
# Claude API Wrapper with Retry
#######################################

# Rate limit backoff multiplier (longer waits for 429)
RATE_LIMIT_BACKOFF_MULTIPLIER=5

# Check if error indicates rate limiting
# Returns 0 (true) if rate limited, 1 (false) otherwise
is_rate_limited() {
    local response="$1"
    local exit_code="$2"

    # Strategy 1: Check JSON error field for rate limit indicators
    if echo "$response" | jq -e '.error // empty' &>/dev/null; then
        local error_msg
        error_msg=$(echo "$response" | jq -r '.error // ""' 2>/dev/null)
        if [[ "$error_msg" =~ (rate.?limit|429|too.?many|overloaded|capacity|throttl) ]]; then
            return 0
        fi
    fi

    # Strategy 2: Check for specific HTTP status codes in response
    local status_code
    status_code=$(echo "$response" | jq -r '.status_code // .http_status // empty' 2>/dev/null)
    if [[ "$status_code" == "429" ]] || [[ "$status_code" == "529" ]] || [[ "$status_code" == "503" ]]; then
        return 0
    fi

    # Strategy 3: Check for rate limit patterns in raw response text
    # More comprehensive pattern matching
    if echo "$response" | grep -qiE \
        "rate.?limit|too.?many.?requests|429|overloaded|capacity|throttl|try.?again.?later|quota.?exceeded|requests?.?per.?(second|minute)|slow.?down"; then
        return 0
    fi

    # Strategy 4: Check for Anthropic-specific error types
    if echo "$response" | jq -e '.error.type // empty' &>/dev/null; then
        local error_type
        error_type=$(echo "$response" | jq -r '.error.type // ""' 2>/dev/null)
        case "$error_type" in
            rate_limit_error|overloaded_error|api_error)
                return 0
                ;;
        esac
    fi

    # Strategy 5: Check for specific exit codes
    # Common exit codes that might indicate rate limiting
    case "$exit_code" in
        29|42|75)  # Various CLI exit codes for rate limits
            return 0
            ;;
    esac

    return 1
}

claude_call() {
    local prompt="$1"
    local schema="$2"
    local model="${3:-haiku}"
    local retry=0
    local backoff=$INITIAL_BACKOFF
    local response
    local exit_code
    local rate_limited=false

    while [[ $retry -lt $MAX_RETRIES ]]; do
        log_debug "API call attempt $((retry + 1))/$MAX_RETRIES (model=$model, timeout=${API_TIMEOUT}s)"

        # Determine fallback model (must be different from main model)
        local fallback_model
        case "$model" in
            sonnet) fallback_model="haiku" ;;
            haiku)  fallback_model="sonnet" ;;
            opus)   fallback_model="sonnet" ;;
            *)      fallback_model="haiku" ;;
        esac

        set +e
        response=$($TIMEOUT_CMD "$API_TIMEOUT" claude -p "$prompt" \
            --json-schema "$schema" \
            --output-format json \
            --model "$model" \
            --fallback-model "$fallback_model" \
            --max-budget-usd "$MAX_COST_PER_CALL" \
            --tools "" \
            2>&1)
        exit_code=$?
        set -e

        rate_limited=false

        # gtimeout/timeout returns 124 when command times out
        if [[ $exit_code -eq 124 ]]; then
            log_warn "API call timed out after ${API_TIMEOUT}s"
        elif [[ $exit_code -eq 0 ]]; then
            local is_error
            is_error=$(echo "$response" | jq -r '.is_error // false')
            if [[ "$is_error" == "false" ]]; then
                track_cost "$response"
                echo "$response"
                return 0
            fi
            local error_msg
            error_msg=$(echo "$response" | jq -r '.error // "unknown"')
            log_warn "API returned error: $error_msg"

            # Check if this was a rate limit error
            if is_rate_limited "$response" "$exit_code"; then
                rate_limited=true
            fi
        else
            log_warn "claude command failed with exit code $exit_code"
            log_debug "Error response: $response"

            # Check if this was a rate limit error
            if is_rate_limited "$response" "$exit_code"; then
                rate_limited=true
            fi
        fi

        retry=$((retry + 1))
        if [[ $retry -lt $MAX_RETRIES ]]; then
            local wait_time=$backoff

            # Apply longer backoff for rate limits
            if [[ "$rate_limited" == "true" ]]; then
                wait_time=$((backoff * RATE_LIMIT_BACKOFF_MULTIPLIER))
                log_warn "Rate limited! Backing off for ${wait_time}s (extended)"
            else
                log_info "Retrying in ${wait_time}s..."
            fi

            sleep $wait_time
            backoff=$((backoff * 2))

            # Cap backoff at 60 seconds for normal errors, 300 for rate limits
            if [[ "$rate_limited" == "true" ]]; then
                [[ $backoff -gt 60 ]] && backoff=60
            else
                [[ $backoff -gt 30 ]] && backoff=30
            fi
        fi
    done

    log_error "All $MAX_RETRIES attempts failed"
    return 1
}

#######################################
# Code Context Extraction
#######################################
extract_code_context() {
    local file="$1"
    local line="$2"
    local ctx="${3:-12}"

    if [[ ! -f "$file" ]]; then
        echo "[File not found: $file]"
        return 1
    fi

    if ! [[ "$line" =~ ^[0-9]+$ ]]; then
        echo "[Invalid line number: $line]"
        return 1
    fi

    local total_lines
    total_lines=$(wc -l < "$file" | tr -d ' ')

    if [[ "$line" -gt "$total_lines" ]] || [[ "$line" -lt 1 ]]; then
        echo "[Line $line out of range (1-$total_lines)]"
        return 1
    fi

    local start_line=$((line - ctx))
    [[ "$start_line" -lt 1 ]] && start_line=1
    local end_line=$((line + ctx))
    [[ "$end_line" -gt "$total_lines" ]] && end_line=$total_lines

    echo '```java'
    echo "// $file:$start_line-$end_line (flagged: L$line)"
    awk -v start="$start_line" -v end="$end_line" -v target="$line" '
        NR >= start && NR <= end {
            prefix = (NR == target) ? ">>>" : "   "
            printf "%s %4d: %s\n", prefix, NR, $0
        }
    ' "$file"
    echo '```'
}

#######################################
# Git Diff Context Extraction
#######################################

# Extract git diff for a specific file around a line
# Args: $1 = file path, $2 = line number, $3 = context lines (default 10)
# Output: Formatted diff output or empty if no diff
extract_git_diff_context() {
    local file="$1"
    local line="$2"
    local ctx="${3:-10}"

    # Get the diff for this file
    local diff_output
    diff_output=$(git diff "$BASE_BRANCH"..."$CURRENT_BRANCH" -- "$file" 2>/dev/null || true)

    if [[ -z "$diff_output" ]]; then
        return
    fi

    # Check if this is a new file (--- /dev/null)
    local is_new_file=false
    if echo "$diff_output" | grep -q '^--- /dev/null'; then
        is_new_file=true
    fi

    local line_count
    line_count=$(echo "$diff_output" | wc -l | tr -d ' ')

    # For new files, include the full diff (up to 200 lines) since all content is new
    if [[ "$is_new_file" == "true" ]]; then
        echo '```diff'
        echo "// New file: $file"
        if [[ "$line_count" -gt 200 ]]; then
            echo "$diff_output" | head -200
            echo "... (truncated, $line_count total lines)"
        else
            echo "$diff_output"
        fi
        echo '```'
        return
    fi

    # For modified files with long diffs, try to find the relevant hunk
    if [[ "$line_count" -gt 50 ]]; then
        echo '```diff'
        echo "// Changes in $file around line $line"
        # Use grep/sed approach for better BSD/GNU compatibility
        # Extract hunks and find the one containing or closest to our target line
        local hunk_output
        hunk_output=$(echo "$diff_output" | awk -v target="$line" '
        /^@@/ {
            # Extract the new file line number from @@ -X,Y +Z,W @@
            gsub(/.*\+/, "", $0)
            gsub(/,.*/, "", $0)
            hunk_start = int($0)
            in_hunk = 1
            current_hunk = ""
            hunk_lines = 0
            next
        }
        in_hunk && /^@@/ {
            # New hunk starting, check if previous was closer
            if (target >= hunk_start && target <= hunk_start + hunk_lines) {
                print current_hunk
                exit
            }
            gsub(/.*\+/, "", $0)
            gsub(/,.*/, "", $0)
            hunk_start = int($0)
            current_hunk = ""
            hunk_lines = 0
            next
        }
        in_hunk {
            current_hunk = current_hunk $0 "\n"
            if (/^[+ ]/) hunk_lines++
        }
        END {
            if (current_hunk != "" && (target == 0 || (target >= hunk_start && target <= hunk_start + hunk_lines + 20))) {
                printf "%s", current_hunk
            }
        }
        ')
        if [[ -n "$hunk_output" ]]; then
            echo "$hunk_output"
        else
            # Fallback: show first 50 lines of diff
            echo "$diff_output" | tail -n +5 | head -50
            echo "... (showing first 50 lines of changes)"
        fi
        echo '```'
    else
        echo '```diff'
        echo "// Changes in $file"
        echo "$diff_output"
        echo '```'
    fi
}

#######################################
# Process Management
#######################################

# Job failure tracking (per-phase)
FAILED_JOBS=0
TOTAL_JOBS=0
FAILED_JOB_PIDS=""

# Cumulative job stats (across all phases)
CUMULATIVE_FAILED_JOBS=0
CUMULATIVE_TOTAL_JOBS=0

reset_job_stats() {
    FAILED_JOBS=0
    TOTAL_JOBS=0
    FAILED_JOB_PIDS=""
}

wait_for_slot() {
    local max_parallel="$1"
    set +u
    while [[ ${#PIDS[@]} -ge $max_parallel ]]; do
        local new_pids=()
        local pid
        for pid in "${PIDS[@]}"; do
            if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
                new_pids+=("$pid")
            else
                # Capture exit status
                local exit_status=0
                wait "$pid" 2>/dev/null || exit_status=$?
                TOTAL_JOBS=$((TOTAL_JOBS + 1))
                if [[ $exit_status -ne 0 ]]; then
                    FAILED_JOBS=$((FAILED_JOBS + 1))
                    FAILED_JOB_PIDS="$FAILED_JOB_PIDS $pid"
                    log_debug "Job $pid failed with exit code $exit_status"
                fi
            fi
        done
        PIDS=("${new_pids[@]}")
        [[ ${#PIDS[@]} -ge $max_parallel ]] && sleep 0.5
    done
    set -u
}

wait_all_pids() {
    set +u
    local pid
    local exit_status
    for pid in "${PIDS[@]}"; do
        if [[ -n "$pid" ]]; then
            exit_status=0
            wait "$pid" 2>/dev/null || exit_status=$?
            TOTAL_JOBS=$((TOTAL_JOBS + 1))
            if [[ $exit_status -ne 0 ]]; then
                FAILED_JOBS=$((FAILED_JOBS + 1))
                FAILED_JOB_PIDS="$FAILED_JOB_PIDS $pid"
                log_debug "Job $pid failed with exit code $exit_status"
            fi
        fi
    done
    PIDS=()
    set -u
}

# Report job failures for current phase and update cumulative stats
report_phase_jobs() {
    local phase_name="$1"

    # Update cumulative stats
    CUMULATIVE_FAILED_JOBS=$((CUMULATIVE_FAILED_JOBS + FAILED_JOBS))
    CUMULATIVE_TOTAL_JOBS=$((CUMULATIVE_TOTAL_JOBS + TOTAL_JOBS))

    if [[ $FAILED_JOBS -gt 0 ]]; then
        log_warn "$phase_name: $FAILED_JOBS/$TOTAL_JOBS jobs failed"
    else
        log_debug "$phase_name: All $TOTAL_JOBS jobs succeeded"
    fi
}

#######################################
# Review Criteria
#######################################
readonly CRITERIA_COMPACT="MUST_FLAG(T1/T2):null-deref,unclosed-resource,shared-mutable-no-sync,Optional.get-no-check,swallowed-exception,raw-types|SHOULD_FLAG(T3/T4):if-chain>3->switch,instanceof-no-pattern,string-concat-loop,Collectors.toList->toList|SKIP:private-from-validated,test-code,generated,io.brane.internal.*"

#######################################
# Phase 0: Setup
#######################################
phase_setup() {
    log_info "========================================"
    log_info "Orchestrator Code Review v7.2"
    log_info "Layered Hallucination Defense"
    log_info "========================================"

    CURRENT_BRANCH=$(git branch --show-current 2>/dev/null || echo "HEAD")
    TODAY=$(date +%Y-%m-%d)

    log_info "Branch: $CURRENT_BRANCH -> $BASE_BRANCH"
    log_info "Models: discovery=$MODEL_DISCOVERY reloc=$MODEL_RELOC verify=$MODEL_VERIFY"
    log_info "Budget: \$$MAX_TOTAL_BUDGET max"
    log_info "Defense: quote=$ENABLE_QUOTE_VERIFICATION reloc=$ENABLE_RELOCALIZATION"
    log_info "========================================"

    if [[ "$CURRENT_BRANCH" == "$BASE_BRANCH" ]]; then
        log_error "Cannot review: already on base branch"
        exit $EXIT_ERROR
    fi

    if ! git rev-parse --verify "$BASE_BRANCH" &>/dev/null; then
        log_error "Base branch '$BASE_BRANCH' not found"
        exit $EXIT_ERROR
    fi

    CHANGED_FILES=$(git diff "$BASE_BRANCH"..."$CURRENT_BRANCH" --name-only -- '*.java' | grep -v '/internal/' | grep -v 'Test\.java$' || true)
    CHANGED_FILE_COUNT=$(echo "$CHANGED_FILES" | grep -c '.' 2>/dev/null || echo "0")

    if [[ "$CHANGED_FILE_COUNT" -eq 0 ]]; then
        log_info "No reviewable Java files changed"
        exit $EXIT_SUCCESS
    fi

    log_info "Files to review: $CHANGED_FILE_COUNT"

    rm -rf "$WORK_DIR"
    mkdir -p "$WORK_DIR"/{discovery,context,verify,reloc,logs}
    mkdir -p "$SPEC_DIR"

    # Initialize cost tracking (must be after WORK_DIR creation)
    init_cost_tracking

    echo "$CHANGED_FILES" | sed '/^$/d' > "$WORK_DIR/files.txt"

    ALL_DISCOVERIES="$WORK_DIR/all_discoveries.json"
    ALL_RESULTS="$WORK_DIR/all_results.json"
    echo '[]' > "$ALL_DISCOVERIES"
    echo '[]' > "$ALL_RESULTS"

    # Initialize hallucination stats (file-based for subshell safety)
    init_hallucination_stats "$WORK_DIR"

    save_checkpoint "setup_complete"
}

#######################################
# Phase 1: Build File Index
#######################################
phase_build_index() {
    log_info "========================================"
    log_info "Phase 1: Build Enumerated File Index"
    log_info "========================================"

    build_file_index "$WORK_DIR/files.txt"
    log_info "Indexed $NUM_FILES files"

    # Generate enumerated header
    build_enumerated_header > "$WORK_DIR/file_header.txt"
}

#######################################
# Phase 2: Discovery
#######################################
build_discovery_prompt_v7() {
    local batch_content="$1"
    local agent_id="$2"

    cat << 'EOF'
You are Discovery Agent reviewing Java code for issues.

## Criteria
EOF
    echo "$CRITERIA_COMPACT"
    cat << 'EOF'

## Files Under Review
EOF
    cat "$WORK_DIR/file_header.txt"
    cat << 'EOF'

## Code to Review
EOF
    echo "$batch_content"
    cat << 'EOF'

## Instructions
Analyze each file and identify potential issues. For each issue found:
- file_index: The bracketed file number [1], [2], etc.
- line_index: The bracketed line number [001], [002], etc.
- code_quote: EXACT verbatim code from the flagged line (copy-paste, NOT paraphrase)
- severity: T1 (confirmed bug), T2 (likely bug), T3 (design issue), T4 (suggestion)
- category: NULL_SAFETY, RESOURCE, THREAD, TYPE, EXCEPTION, PATTERN, or OTHER
- description: Brief description of the issue (must be specific)

## DO NOT FLAG (False Positive Examples)
These patterns look like issues but are NOT bugs. DO NOT report them:

### NULL_SAFETY - Safe Patterns
- Parameter already validated earlier in method: `if (x == null) throw ...` then `x.method()` is SAFE
- Optional.get() AFTER isPresent() check in same scope
- Objects.requireNonNull() was called before use
- @NonNull/@NotNull annotated parameters (framework validates)
- Null check exists in calling code (trace the call)

### RESOURCE - Safe Patterns
- try-with-resources: `try (var stream = ...)` auto-closes, do NOT flag
- Resource closed in finally block
- Resource passed to another method that takes ownership
- Resource stored in field for later cleanup

### THREAD - Safe Patterns
- Effectively immutable objects (all fields final, no setters)
- Thread-confined objects (created and used in same thread)
- Synchronized access exists but in different method
- Using concurrent collections (ConcurrentHashMap, etc.)

### EXCEPTION - Safe Patterns
- Catch block logs and re-throws: `catch(E e) { log(e); throw e; }`
- Catch block returns error value: `catch(E e) { return Optional.empty(); }`
- Exception is part of control flow (expected behavior)

### TYPE - Safe Patterns
- Raw types in legacy code compatibility layers
- Unchecked cast with instanceof check: `if (x instanceof Foo f)`
- Generic type erasure at boundaries (unavoidable)

## CRITICAL RULES
- Use ONLY the bracketed numbers shown in the code
- code_quote MUST be an exact copy from the code, not a description
- Line numbers MUST match the actual code shown
- Skip io.brane.internal.* packages
- Focus on real issues, not style preferences
- When uncertain, DO NOT report (prefer false negatives over false positives)
- A finding with no clear bug is worse than missing a minor issue
EOF
}

#######################################
# Single Discovery Pass (helper for self-consistency)
#######################################
run_single_discovery_pass() {
    local run_id="$1"
    local output_dir="$2"

    mkdir -p "$output_dir"

    # Build batches with enumerated content
    local batch_num=0
    local current_content=""
    local current_size=0
    local max_batch_size=10000

    # Get file indices - optionally shuffle for variation
    local indices=()
    for idx in "${!FILE_INDEX[@]}"; do
        indices+=("$idx")
    done

    # Shuffle indices for runs > 0 to introduce variation
    if [[ "$run_id" -gt 0 ]]; then
        # Simple shuffle using sort with random key
        local shuffled
        shuffled=$(printf '%s\n' "${indices[@]}" | while read -r i; do echo "$RANDOM $i"; done | sort -n | cut -d' ' -f2)
        indices=()
        while IFS= read -r idx; do
            indices+=("$idx")
        done <<< "$shuffled"
    fi

    for idx in "${indices[@]}"; do
        local file="${FILE_INDEX[$idx]}"
        [[ ! -f "$file" ]] && continue

        local content
        content=$(format_file_with_line_numbers "$file" "$MAX_LINES_PER_CHUNK")
        local content_size=${#content}

        if [[ $((current_size + content_size)) -gt $max_batch_size ]] && [[ -n "$current_content" ]]; then
            echo "$current_content" > "$output_dir/batch_${batch_num}_content.txt"
            batch_num=$((batch_num + 1))
            current_content=""
            current_size=0
        fi

        current_content+="
### [$idx] $(basename "$file")
\`\`\`java
$content
\`\`\`
"
        current_size=$((current_size + content_size + 50))
    done

    if [[ -n "$current_content" ]]; then
        echo "$current_content" > "$output_dir/batch_${batch_num}_content.txt"
        batch_num=$((batch_num + 1))
    fi

    log_debug "Run $run_id: Created $batch_num batches"

    # Process batches with parallelism
    PIDS=()
    local batch_id=0

    for batch_file in "$output_dir"/batch_*_content.txt; do
        [[ ! -f "$batch_file" ]] && continue

        wait_for_slot "$MAX_PARALLEL_DISCOVERY"

        local bid=$batch_id
        local rid=$run_id
        (
            local content
            content=$(cat "$batch_file")
            local prompt
            prompt=$(build_discovery_prompt_v7 "$content" "$bid")

            local response
            if response=$(claude_call "$prompt" "$DISCOVERY_SCHEMA_V7" "$MODEL_DISCOVERY"); then
                echo "$response" | jq -r '.structured_output // empty' > "$output_dir/batch_${bid}_result.json"
                log_debug "Run $rid Batch $bid completed"
            else
                log_warn "Run $rid Batch $bid failed"
                echo '{"findings":[],"clean_files":[]}' > "$output_dir/batch_${bid}_result.json"
            fi
        ) &
        PIDS+=($!)
        BACKGROUND_PIDS+=($!)
        batch_id=$((batch_id + 1))
    done

    wait_all_pids
    echo "$batch_num"
}

#######################################
# Aggregate findings from a discovery pass
#######################################
aggregate_discovery_pass() {
    local output_dir="$1"
    local findings_file="$2"

    : > "$findings_file"

    for result_file in "$output_dir"/batch_*_result.json; do
        [[ ! -f "$result_file" ]] && continue

        local raw_findings
        raw_findings=$(jq -r '.findings // []' "$result_file" 2>/dev/null || echo '[]')

        while IFS= read -r finding; do
            [[ -z "$finding" ]] || [[ "$finding" == "null" ]] && continue

            local file_idx line_idx
            # Normalize indices to integers (handles both "123" and 123)
            file_idx=$(echo "$finding" | jq -r '.file_index // 0 | tonumber | floor')
            line_idx=$(echo "$finding" | jq -r '.line_index // 0 | tonumber | floor')

            # Layer 1: Validate indices
            local validation
            validation=$(validate_indices "$file_idx" "$line_idx")

            if [[ "$validation" != "VALID" ]]; then
                log_debug "Invalid indices: $validation (file=$file_idx, line=$line_idx)"
                increment_stat "invalid_index"
                continue
            fi

            # Resolve to actual path
            local resolved
            resolved=$(resolve_indices "$file_idx" "$line_idx")
            local file_path="${resolved%%|*}"
            local line_num="${resolved##*|}"

            # Add resolved path to finding
            echo "$finding" | jq -c \
                --arg file "$file_path" \
                --argjson line "$line_num" \
                '. + {file: $file, line: $line}' >> "$findings_file"
        done < <(echo "$raw_findings" | jq -c '.[]')
    done
}

#######################################
# Self-consistency voting across multiple runs
#######################################
apply_self_consistency_voting() {
    local num_runs="$1"
    local threshold="$2"
    shift 2
    local findings_files=("$@")

    log_info "Applying self-consistency voting (threshold=$threshold, runs=$num_runs)..."

    # Create a vote count for each unique finding (file:line:category)
    local vote_file="$WORK_DIR/discovery/votes.jsonl"
    : > "$vote_file"

    # Collect all findings with their keys
    for findings_file in "${findings_files[@]}"; do
        [[ ! -f "$findings_file" ]] && continue
        while IFS= read -r finding; do
            [[ -z "$finding" ]] && continue
            # Key: file + line (within tolerance) + category
            local key
            key=$(echo "$finding" | jq -r '"\(.file):\(.line):\(.category)"')
            echo "$key|$finding" >> "$vote_file"
        done < "$findings_file"
    done

    # Count votes for each unique finding key
    local consensus_file="$WORK_DIR/discovery/consensus.jsonl"
    : > "$consensus_file"

    # Group by key and count, keeping best finding for each key
    local min_votes
    min_votes=$(echo "$num_runs * $threshold" | bc | cut -d. -f1)
    [[ "$min_votes" -lt 1 ]] && min_votes=1

    log_debug "Minimum votes required: $min_votes (of $num_runs runs)"

    # Use awk to count votes and select findings meeting threshold
    # For findings at nearby lines (±3), consider them the same
    local counted_file="$WORK_DIR/discovery/vote_counts.txt"
    sort "$vote_file" | awk -F'|' '
    {
        key = $1
        finding = $2
        votes[key]++
        if (!(key in best_finding)) {
            best_finding[key] = finding
        }
    }
    END {
        for (key in votes) {
            print votes[key] "|" key "|" best_finding[key]
        }
    }
    ' | sort -t'|' -k1 -rn > "$counted_file"

    # Filter to findings meeting threshold
    local passed=0
    local rejected=0
    while IFS='|' read -r count key finding; do
        if [[ "$count" -ge "$min_votes" ]]; then
            echo "$finding" >> "$consensus_file"
            passed=$((passed + 1))
        else
            rejected=$((rejected + 1))
            log_debug "Self-consistency rejected (${count}/${num_runs} votes): $key"
            increment_stat "self_consistency_rejected"
        fi
    done < "$counted_file"

    log_info "Self-consistency voting: $passed passed, $rejected rejected"
    echo "$consensus_file"
}

phase_discovery() {
    log_info "========================================"
    log_info "Phase 2: Discovery (Enumerated Format)"
    if [[ "$ENABLE_SELF_CONSISTENCY" == "true" ]]; then
        log_info "Self-Consistency: $SELF_CONSISTENCY_RUNS runs, ${SELF_CONSISTENCY_THRESHOLD} threshold"
    fi
    log_info "========================================"

    local findings_files=()
    local total_batches=0

    if [[ "$ENABLE_SELF_CONSISTENCY" == "true" ]]; then
        # Multi-run discovery with voting
        reset_job_stats

        local run_id=0
        while [[ $run_id -lt $SELF_CONSISTENCY_RUNS ]]; do
            log_info "Discovery run $((run_id + 1))/$SELF_CONSISTENCY_RUNS..."
            local run_dir="$WORK_DIR/discovery/run_${run_id}"
            local batches
            batches=$(run_single_discovery_pass "$run_id" "$run_dir")
            total_batches=$((total_batches + batches))

            # Aggregate this run's findings
            local run_findings="$run_dir/findings.jsonl"
            aggregate_discovery_pass "$run_dir" "$run_findings"
            findings_files+=("$run_findings")

            check_budget
            run_id=$((run_id + 1))
        done

        report_phase_jobs "Discovery (${SELF_CONSISTENCY_RUNS} runs)"

        # Apply voting
        local consensus_file
        consensus_file=$(apply_self_consistency_voting "$SELF_CONSISTENCY_RUNS" "$SELF_CONSISTENCY_THRESHOLD" "${findings_files[@]}")

        # Aggregate consensus findings
        if [[ -s "$consensus_file" ]]; then
            jq -s '.' "$consensus_file" | deduplicate_json_array_semantic 5 > "$ALL_DISCOVERIES"
        else
            echo '[]' > "$ALL_DISCOVERIES"
        fi

        NUM_BATCHES=$total_batches
    else
        # Single-run discovery (original behavior)
        reset_job_stats
        local run_dir="$WORK_DIR/discovery"
        NUM_BATCHES=$(run_single_discovery_pass "0" "$run_dir")
        report_phase_jobs "Discovery"
        check_budget

        local findings_tmp="$run_dir/findings_resolved.jsonl"
        aggregate_discovery_pass "$run_dir" "$findings_tmp"

        if [[ -s "$findings_tmp" ]]; then
            jq -s '.' "$findings_tmp" | deduplicate_json_array_semantic 5 > "$ALL_DISCOVERIES"
        else
            echo '[]' > "$ALL_DISCOVERIES"
        fi
    fi

    DISCOVERY_COUNT=$(jq 'length' "$ALL_DISCOVERIES")
    log_info "Discovery complete: $DISCOVERY_COUNT findings (after validation)"

    if [[ "$DISCOVERY_COUNT" -eq 0 ]]; then
        generate_clean_report
        exit $EXIT_SUCCESS
    fi

    # Enforce limit with priority sorting
    if [[ "$DISCOVERY_COUNT" -gt "$MAX_FINDINGS_TO_VERIFY" ]]; then
        log_warn "Limiting to $MAX_FINDINGS_TO_VERIFY findings (prioritizing T1/T2)"
        local all_findings
        all_findings=$(jq --argjson max "$MAX_FINDINGS_TO_VERIFY" '
            sort_by(
                if .severity == "T1" then 0
                elif .severity == "T2" then 1
                elif .severity == "T3" then 2
                else 3 end
            ) | .[:$max]
        ' "$ALL_DISCOVERIES")
        echo "$all_findings" > "$ALL_DISCOVERIES"
        DISCOVERY_COUNT=$MAX_FINDINGS_TO_VERIFY
    fi

    save_checkpoint "discovery_complete"
}

#######################################
# Phase 3: Quote Verification
#######################################
phase_quote_verification() {
    if [[ "$ENABLE_QUOTE_VERIFICATION" != "true" ]]; then
        log_info "Quote verification disabled, skipping..."
        return
    fi

    log_info "========================================"
    log_info "Phase 3: Quote Verification (Layer 2)"
    log_info "========================================"

    local verified_tmp="$WORK_DIR/quote_verified.jsonl"
    : > "$verified_tmp"
    local quote_verified=0
    local quote_corrected=0
    local quote_rejected=0

    while IFS= read -r finding; do
        [[ -z "$finding" ]] || [[ "$finding" == "null" ]] && continue

        local file line quote
        file=$(echo "$finding" | jq -r '.file // empty')
        line=$(echo "$finding" | jq -r '.line // 0')
        quote=$(echo "$finding" | jq -r '.code_quote // empty')

        local result
        result=$(verify_quote "$file" "$line" "$quote")
        local status="${result%%:*}"

        if [[ "$status" == "MATCH" ]]; then
            local match_info="${result#MATCH:}"
            local match_type="${match_info%%:*}"
            local actual_line="${match_info##*:}"

            if [[ "$match_type" == "exact" ]]; then
                # Exact match, keep as-is
                echo "$finding" >> "$verified_tmp"
                quote_verified=$((quote_verified + 1))
            elif [[ "$match_type" == "nearby" ]] || [[ "$match_type" == "partial" ]]; then
                # Correct the line number
                echo "$finding" | jq -c --argjson line "$actual_line" '.line = $line' >> "$verified_tmp"
                quote_corrected=$((quote_corrected + 1))
                increment_stat "quote_corrected"
                log_debug "Corrected line: $file:$line -> $actual_line"
            elif [[ "$match_type" == "elsewhere" ]]; then
                # Found but far from claimed location - flag but include
                echo "$finding" | jq -c --argjson line "$actual_line" \
                    '. + {line: $line, quote_warning: "location_drift"}' >> "$verified_tmp"
                quote_corrected=$((quote_corrected + 1))
                increment_stat "quote_corrected"
                log_warn "Location drift: $file:$line -> $actual_line"
            fi
        else
            # Quote not found - reject
            quote_rejected=$((quote_rejected + 1))
            increment_stat "quote_rejected"
            log_debug "Quote rejected: $file:$line - $result"
        fi
    done < <(jq -c '.[]' "$ALL_DISCOVERIES")

    # Aggregate from file (single jq call)
    if [[ -s "$verified_tmp" ]]; then
        jq -s '.' "$verified_tmp" > "$ALL_DISCOVERIES"
    else
        echo '[]' > "$ALL_DISCOVERIES"
    fi

    local remaining
    remaining=$(jq 'length' "$ALL_DISCOVERIES")
    log_info "Quote verification: $quote_verified exact, $quote_corrected corrected, $quote_rejected rejected"
    log_info "Findings remaining: $remaining"

    save_checkpoint "quote_verification_complete"
}

#######################################
# Phase 4: Re-localization
#######################################
phase_relocalization() {
    if [[ "$ENABLE_RELOCALIZATION" != "true" ]]; then
        log_info "Re-localization disabled, skipping..."
        return
    fi

    log_info "========================================"
    log_info "Phase 4: Re-localization (Layer 3)"
    log_info "========================================"

    local findings_count
    findings_count=$(jq 'length' "$ALL_DISCOVERIES")

    if [[ "$findings_count" -eq 0 ]]; then
        log_info "No findings to re-localize"
        return
    fi

    log_info "Re-localizing $findings_count findings with $MODEL_RELOC..."

    local reloc_verified_tmp="$WORK_DIR/reloc_verified.jsonl"
    : > "$reloc_verified_tmp"
    local agree_count=0
    local disagree_count=0
    local not_found_count=0

    # Process findings in parallel
    PIDS=()
    reset_job_stats
    local finding_id=0

    while IFS= read -r finding; do
        [[ -z "$finding" ]] || [[ "$finding" == "null" ]] && continue

        wait_for_slot "$MAX_PARALLEL_RELOC"

        local fid=$finding_id
        local file category description discovery_line
        file=$(echo "$finding" | jq -r '.file')
        category=$(echo "$finding" | jq -r '.category // "OTHER"')
        description=$(echo "$finding" | jq -r '.description')
        discovery_line=$(echo "$finding" | jq -r '.line')

        # Save finding for later processing
        echo "$finding" > "$WORK_DIR/reloc/finding_${fid}.json"

        (
            local prompt
            prompt=$(build_reloc_prompt "$file" "$category" "$description")

            local response
            if response=$(claude_call "$prompt" "$RELOC_SCHEMA" "$MODEL_RELOC"); then
                echo "$response" | jq -r '.structured_output // empty' > "$WORK_DIR/reloc/result_${fid}.json"
            else
                echo '{"found": false, "line": null, "confidence": "LOW", "reasoning": "API call failed"}' \
                    > "$WORK_DIR/reloc/result_${fid}.json"
            fi
        ) &
        PIDS+=($!)
        BACKGROUND_PIDS+=($!)
        finding_id=$((finding_id + 1))
    done < <(jq -c '.[]' "$ALL_DISCOVERIES")

    log_info "Waiting for re-localization..."
    wait_all_pids
    report_phase_jobs "Re-localization"
    check_budget

    # Process results
    local fid=0
    while [[ $fid -lt $finding_id ]]; do
        local finding_file="$WORK_DIR/reloc/finding_${fid}.json"
        local result_file="$WORK_DIR/reloc/result_${fid}.json"

        if [[ ! -f "$finding_file" ]] || [[ ! -f "$result_file" ]]; then
            fid=$((fid + 1))
            continue
        fi

        local finding result
        finding=$(cat "$finding_file")
        result=$(cat "$result_file")

        local discovery_line discovery_desc reloc_line reloc_found reloc_issue_type
        # Normalize line numbers to integers
        discovery_line=$(echo "$finding" | jq -r '.line | tonumber | floor')
        discovery_desc=$(echo "$finding" | jq -r '.description // ""')
        reloc_found=$(echo "$result" | jq -r '.found // false')
        reloc_line=$(echo "$result" | jq -r 'if .line == null then "null" else (.line | tonumber | floor | tostring) end')
        reloc_issue_type=$(echo "$result" | jq -r '.issue_type // ""')

        local comparison
        if [[ "$reloc_found" == "true" ]]; then
            # Pass description and issue_type for semantic comparison
            comparison=$(compare_locations "$discovery_line" "$reloc_line" "$RELOC_TOLERANCE" "$discovery_desc" "$reloc_issue_type")
        else
            comparison="NOT_FOUND"
        fi

        case "$comparison" in
            AGREE)
                echo "$finding" >> "$reloc_verified_tmp"
                agree_count=$((agree_count + 1))
                increment_stat "passed"
                ;;
            AGREE_WEAK*)
                # Location matches but semantic mismatch - flag but include
                echo "$finding" | jq -c --arg comp "$comparison" \
                    '. + {reloc_warning: $comp}' >> "$reloc_verified_tmp"
                agree_count=$((agree_count + 1))
                increment_stat "semantic_mismatch"
                log_debug "Re-loc semantic mismatch: $comparison"
                ;;
            DISAGREE*)
                # Include but flag for review
                echo "$finding" | jq -c --arg comp "$comparison" \
                    '. + {reloc_warning: $comp}' >> "$reloc_verified_tmp"
                disagree_count=$((disagree_count + 1))
                increment_stat "reloc_disagree"
                log_debug "Re-loc disagree: $comparison"
                ;;
            NOT_FOUND)
                not_found_count=$((not_found_count + 1))
                increment_stat "reloc_not_found"
                local file
                file=$(echo "$finding" | jq -r '.file')
                log_debug "Re-loc not found: $file:$discovery_line"
                ;;
        esac
        fid=$((fid + 1))
    done

    # Aggregate from file (single jq call)
    if [[ -s "$reloc_verified_tmp" ]]; then
        jq -s '.' "$reloc_verified_tmp" > "$ALL_DISCOVERIES"
    else
        echo '[]' > "$ALL_DISCOVERIES"
    fi

    local remaining
    remaining=$(jq 'length' "$ALL_DISCOVERIES")
    log_info "Re-localization: $agree_count agree, $disagree_count disagree, $not_found_count not found"
    log_info "Findings remaining: $remaining"

    save_checkpoint "relocalization_complete"
}

#######################################
# Phase 5: Build Context
#######################################
phase_build_context() {
    log_info "========================================"
    log_info "Phase 5: Build Code Context"
    log_info "========================================"

    local files_with_findings
    files_with_findings=$(jq -r '.[].file' "$ALL_DISCOVERIES" | sort -u)
    local file_count
    file_count=$(echo "$files_with_findings" | grep -c '.' || echo "0")

    log_info "Building context for $file_count files..."

    while IFS= read -r file_path; do
        [[ -z "$file_path" ]] && continue

        local safe_name
        safe_name=$(echo "$file_path" | tr '/' '_')
        local context_file="$WORK_DIR/context/${safe_name}.json"

        local file_findings
        file_findings=$(jq --arg f "$file_path" '[.[] | select(.file == $f)]' "$ALL_DISCOVERIES")
        local finding_count
        finding_count=$(echo "$file_findings" | jq 'length')

        if [[ "$finding_count" -gt "$MAX_FINDINGS_PER_FILE" ]]; then
            file_findings=$(echo "$file_findings" | jq --argjson max "$MAX_FINDINGS_PER_FILE" '.[:$max]')
        fi

        # Get git diff for this file (once per file, not per finding)
        local file_diff
        file_diff=$(extract_git_diff_context "$file_path" 0 2>/dev/null || true)

        local context_tmp="${context_file}.tmp"
        : > "$context_tmp"
        while IFS= read -r finding; do
            local line severity
            line=$(echo "$finding" | jq -r '.line')
            severity=$(echo "$finding" | jq -r '.severity')

            local ctx_lines=12
            case "$severity" in
                T1) ctx_lines=20 ;;
                T2) ctx_lines=15 ;;
                T3) ctx_lines=10 ;;
                T4) ctx_lines=5 ;;
            esac

            local code_snippet
            code_snippet=$(extract_code_context "$file_path" "$line" "$ctx_lines" 2>/dev/null || echo "[Could not extract]")

            # Include git diff context if available
            echo "$finding" | jq -c \
                --arg code "$code_snippet" \
                --arg diff "$file_diff" \
                '. + {code_context: $code, git_diff: $diff}' >> "$context_tmp"
        done < <(echo "$file_findings" | jq -c '.[]')

        # Aggregate context entries (single jq call)
        jq -s '.' "$context_tmp" > "$context_file"
        rm -f "$context_tmp"
        log_debug "  $file_path: $finding_count findings"
    done <<< "$files_with_findings"
}

#######################################
# Phase 6: Verification
#######################################
build_verify_prompt() {
    local context_json="$1"

    cat << EOF
Verify the following potential issues by analyzing the actual code AND the git diff showing what changed.

## Findings to Verify
$(echo "$context_json" | jq -r '
    .[] |
    "### \(.severity) at \(.file):\(.line)\n" +
    "Category: \(.category)\n" +
    "Description: \(.description)\n\n" +
    "**Current Code:**\n\(.code_context)\n\n" +
    (if .git_diff != "" then "**What Changed (git diff):**\n\(.git_diff)\n" else "" end)
')

## Instructions
For each finding:
1. Look at the flagged line in the code context
2. Check the git diff to see what was actually changed (lines starting with + are new, - are removed)
3. Determine if the issue actually exists IN THE CHANGED CODE
4. Check for mitigating factors (null checks elsewhere, try-with-resources, etc.)
5. Issue a verdict

IMPORTANT: Focus on the CHANGED lines. If the flagged issue is in code that wasn't modified,
it may be pre-existing and lower priority than issues in newly written code.

Verdicts:
- CONFIRMED: Issue is real and should be fixed
- REJECTED: False positive, issue doesn't exist
- DOWNGRADE_T3: Issue exists but is less severe (design concern)
- DOWNGRADE_T4: Issue exists but is minor (suggestion only)

For confirmed issues, provide:
- severity: Final severity (T1/T2/T3/T4)
- title: Short descriptive title
- evidence: What you see in the code (1 sentence)
- fix: How to fix it (1 sentence)

Rules:
- Base verdicts ONLY on code shown
- If uncertain -> REJECTED (avoid false positives)
- Prioritize issues in newly changed code (+ lines in diff)
- Be concise
EOF
}

phase_verification() {
    log_info "========================================"
    log_info "Phase 6: Verification"
    log_info "========================================"

    local context_files
    context_files=$(ls "$WORK_DIR/context"/*.json 2>/dev/null || true)
    local context_count
    context_count=$(echo "$context_files" | grep -c '.' 2>/dev/null || echo "0")

    if [[ "$context_count" -eq 0 ]]; then
        log_warn "No context files to verify"
        return
    fi

    log_info "Verifying $context_count files (model=$MODEL_VERIFY)..."

    PIDS=()
    reset_job_stats
    for context_file in $context_files; do
        wait_for_slot "$MAX_PARALLEL_VERIFY"

        local safe_name
        safe_name=$(basename "$context_file" .json)

        (
            local context_json
            context_json=$(cat "$context_file")
            local prompt
            prompt=$(build_verify_prompt "$context_json")

            local response
            if response=$(claude_call "$prompt" "$VERIFY_SCHEMA" "$MODEL_VERIFY"); then
                echo "$response" | jq -r '.structured_output // empty' > "$WORK_DIR/verify/${safe_name}_result.json"
            else
                log_warn "Verification failed for $safe_name"
                echo '{"verdicts":[],"learnings":[]}' > "$WORK_DIR/verify/${safe_name}_result.json"
            fi
        ) &
        PIDS+=($!)
        BACKGROUND_PIDS+=($!)
    done

    log_info "Waiting for verification..."
    wait_all_pids
    report_phase_jobs "Verification"
    check_budget

    local all_verdicts='[]'
    for result_file in "$WORK_DIR/verify"/*_result.json; do
        [[ ! -f "$result_file" ]] && continue
        local verdicts
        verdicts=$(jq -r '.verdicts // []' "$result_file" 2>/dev/null || echo '[]')
        all_verdicts=$(echo "$all_verdicts" "$verdicts" | jq -s 'add')
    done

    echo "$all_verdicts" > "$ALL_RESULTS"

    VERIFIED_COUNT=$(echo "$all_verdicts" | jq '[.[] | select(.verdict == "CONFIRMED" or .verdict == "DOWNGRADE_T3" or .verdict == "DOWNGRADE_T4")] | length')
    REJECTED_COUNT=$(echo "$all_verdicts" | jq '[.[] | select(.verdict == "REJECTED")] | length')

    log_info "Verification: $VERIFIED_COUNT confirmed, $REJECTED_COUNT rejected"

    save_checkpoint "verification_complete"
}

#######################################
# Phase 7: Opus Review
#######################################
build_opus_prompt() {
    local findings_json="$1"

    cat << EOF
You are a senior code reviewer providing a second opinion on high-severity findings.

Another reviewer identified these T1/T2 issues. Your job:
1. Verify each finding is a REAL issue (not false positive)
2. Check if severity is correct (T1 vs T2)
3. Be rigorous - T1/T2 means developer must act

## Findings to Review
$(echo "$findings_json" | jq -r '.[] | "### \(.severity // "T2") at \(.file):\(.line)\nTitle: \(.title // "Untitled")\nFix: \(.fix // "No fix provided")\n\(.code_context // "[No code]")\n"')

## Instructions
For each finding, provide:
- original_file: the file path
- original_line: the line number
- verdict: AGREE (confirm issue), DISAGREE (reject as false positive), UPGRADE_T1 (make more severe), DOWNGRADE_T3 (make less severe)
- reason: why you made this decision (1 sentence)

Rules:
- If uncertain, DISAGREE (avoid false positives)
- T1 = definitely a bug that will cause problems
- T2 = likely a bug that could cause problems
EOF
}

# Opus batch size - small batches to avoid position bias
OPUS_BATCH_SIZE=2

phase_opus_review() {
    local t1t2_findings
    t1t2_findings=$(jq '[.[] | select((.verdict == "CONFIRMED" or .verdict == "DOWNGRADE_T3" or .verdict == "DOWNGRADE_T4") and (.severity == "T1" or .severity == "T2"))]' "$ALL_RESULTS")
    local t1t2_count
    t1t2_count=$(echo "$t1t2_findings" | jq 'length')

    if [[ "$ENABLE_OPUS_REVIEW" != "true" ]]; then
        log_info "Opus review disabled"
        return
    fi

    if [[ "$t1t2_count" -lt "$MIN_T1T2_FOR_OPUS" ]]; then
        log_info "Skipping Opus review (only $t1t2_count T1/T2 findings, need $MIN_T1T2_FOR_OPUS)"
        return
    fi

    log_info "========================================"
    log_info "Phase 7: Opus Second Opinion"
    log_info "========================================"

    # Process in small batches to avoid position bias
    local num_batches=$(( (t1t2_count + OPUS_BATCH_SIZE - 1) / OPUS_BATCH_SIZE ))
    log_info "Reviewing $t1t2_count findings in $num_batches batches (batch_size=$OPUS_BATCH_SIZE)"

    local overrides=0
    local batch_idx=0

    while [[ $batch_idx -lt $num_batches ]]; do
        local start_idx=$((batch_idx * OPUS_BATCH_SIZE))
        local batch_findings
        batch_findings=$(echo "$t1t2_findings" | jq --argjson start "$start_idx" --argjson size "$OPUS_BATCH_SIZE" '.[$start:$start+$size]')

        local batch_count
        batch_count=$(echo "$batch_findings" | jq 'length')

        if [[ "$batch_count" -eq 0 ]]; then
            batch_idx=$((batch_idx + 1))
            continue
        fi

        log_info "  Batch $((batch_idx + 1))/$num_batches ($batch_count findings)..."

        # Build context for this batch
        local opus_context_tmp="$WORK_DIR/opus_batch_${batch_idx}.jsonl"
        : > "$opus_context_tmp"
        while IFS= read -r finding; do
            local file line
            file=$(echo "$finding" | jq -r '.file')
            line=$(echo "$finding" | jq -r '.line')
            local code_snippet
            code_snippet=$(extract_code_context "$file" "$line" 15 2>/dev/null || echo "[Could not extract]")
            echo "$finding" | jq -c --arg code "$code_snippet" '. + {code_context: $code}' >> "$opus_context_tmp"
        done < <(echo "$batch_findings" | jq -c '.[]')

        local findings_with_context
        findings_with_context=$(jq -s '.' "$opus_context_tmp")

        local prompt
        prompt=$(build_opus_prompt "$findings_with_context")

        local response
        if ! response=$(claude_call "$prompt" "$OPUS_SCHEMA" "$MODEL_OPUS"); then
            log_warn "    Batch $((batch_idx + 1)) failed, keeping original verdicts"
            batch_idx=$((batch_idx + 1))
            continue
        fi

        local opus_reviews
        opus_reviews=$(echo "$response" | jq -r '.structured_output.reviews // []')

        while IFS= read -r review; do
            local orig_file orig_line verdict reason
            orig_file=$(echo "$review" | jq -r '.original_file')
            orig_line=$(echo "$review" | jq -r '.original_line')
            verdict=$(echo "$review" | jq -r '.verdict')
            reason=$(echo "$review" | jq -r '.reason')

            case "$verdict" in
                DISAGREE)
                    local updated
                    updated=$(jq --arg f "$orig_file" --argjson l "$orig_line" \
                        '[.[] | select(.file != $f or .line != $l)]' "$ALL_RESULTS")
                    echo "$updated" > "$ALL_RESULTS"
                    log_info "    Opus rejected: $orig_file:$orig_line - $reason"
                    overrides=$((overrides + 1))
                    ;;
                UPGRADE_T1)
                    local updated
                    updated=$(jq --arg f "$orig_file" --argjson l "$orig_line" \
                        '[.[] | if .file == $f and .line == $l then .severity = "T1" else . end]' "$ALL_RESULTS")
                    echo "$updated" > "$ALL_RESULTS"
                    log_info "    Opus upgraded to T1: $orig_file:$orig_line"
                    overrides=$((overrides + 1))
                    ;;
                DOWNGRADE_T3)
                    local updated
                    updated=$(jq --arg f "$orig_file" --argjson l "$orig_line" \
                        '[.[] | if .file == $f and .line == $l then .severity = "T3" else . end]' "$ALL_RESULTS")
                    echo "$updated" > "$ALL_RESULTS"
                    log_info "    Opus downgraded to T3: $orig_file:$orig_line"
                    overrides=$((overrides + 1))
                    ;;
            esac
        done < <(echo "$opus_reviews" | jq -c '.[]')

        check_budget
        batch_idx=$((batch_idx + 1))
    done

    OPUS_OVERRIDES=$overrides
    log_info "Opus review complete: $overrides changes made"
}

#######################################
# Phase 8: Generate Report
#######################################
generate_clean_report() {
    cat > "$REVIEW_MD" << EOF
# Code Review: ${CURRENT_BRANCH}

**Date**: ${TODAY}
**Files Changed**: ${CHANGED_FILE_COUNT}
**Mode**: Orchestrator v7 (Layered Defense)

## Summary
No issues identified. Code follows established patterns.

## Findings
### T1: Confirmed Bugs
None.

### T2: Potential Bugs
None.

### T3: Design Concerns
None.

### T4: Suggestions
None.

---
*Total cost: \$${TOTAL_COST}*
EOF
    log_info "Clean report generated: $REVIEW_MD"
}

generate_simple_report() {
    local findings="$1"

    {
        echo "# Code Review: ${CURRENT_BRANCH}"
        echo ""
        echo "**Date**: ${TODAY}  "
        echo "**Base**: ${BASE_BRANCH}  "
        echo "**Files Changed**: ${CHANGED_FILE_COUNT}  "
        echo "**Findings**: ${VERIFIED_COUNT} verified, ${REJECTED_COUNT} rejected  "
        echo "**Mode**: Orchestrator v7 (Layered Hallucination Defense)"
        echo ""
        echo "## Findings"
        echo ""

        for tier in T1 T2 T3 T4; do
            case $tier in
                T1) echo "### T1: Confirmed Bugs" ;;
                T2) echo "### T2: Potential Bugs" ;;
                T3) echo "### T3: Design Concerns" ;;
                T4) echo "### T4: Suggestions" ;;
            esac
            echo ""

            local tier_findings
            tier_findings=$(echo "$findings" | jq --arg t "$tier" '[.[] | select(.severity == $t)]')
            local tier_count
            tier_count=$(echo "$tier_findings" | jq 'length')

            if [[ "$tier_count" -gt 0 ]]; then
                local idx=1
                while IFS= read -r finding; do
                    local title file line fix
                    title=$(echo "$finding" | jq -r '.title // "Untitled"')
                    file=$(echo "$finding" | jq -r '.file')
                    line=$(echo "$finding" | jq -r '.line')
                    fix=$(echo "$finding" | jq -r '.fix // "No fix provided"')

                    printf '#### %s-%03d: %s\n' "$tier" "$idx" "$title"
                    echo "- **File**: \`$file:$line\`"
                    echo "- **Fix**: $fix"
                    echo ""
                    idx=$((idx + 1))
                done < <(echo "$tier_findings" | jq -c '.[]')
            else
                echo "None."
                echo ""
            fi
        done

        echo "---"
        echo "*Total cost: \$${TOTAL_COST}*"
    } > "$REVIEW_MD"

    log_info "Report generated: $REVIEW_MD"
}

phase_generate_report() {
    log_info "========================================"
    log_info "Phase 8: Generate Report"
    log_info "========================================"

    local verified
    verified=$(jq '[.[] | select(.verdict == "CONFIRMED" or .verdict == "DOWNGRADE_T3" or .verdict == "DOWNGRADE_T4")]' "$ALL_RESULTS")
    local verified_count
    verified_count=$(echo "$verified" | jq 'length')

    if [[ "$verified_count" -eq 0 ]]; then
        generate_clean_report
        return
    fi

    log_info "Generating report ($verified_count findings)"
    generate_simple_report "$verified"
}

#######################################
# Phase 9: Extract Tasks
#######################################
phase_extract_tasks() {
    log_info "Extracting tasks..."

    : > "$TASKS_TXT"

    local verified
    verified=$(jq '[.[] | select(.verdict == "CONFIRMED" or .verdict == "DOWNGRADE_T3" or .verdict == "DOWNGRADE_T4")] | sort_by(.severity)' "$ALL_RESULTS")

    local task_num=1
    while IFS= read -r finding; do
        local sev file line title fix
        sev=$(echo "$finding" | jq -r '.severity // "T3"')
        file=$(echo "$finding" | jq -r '.file')
        line=$(echo "$finding" | jq -r '.line')
        title=$(echo "$finding" | jq -r '.title // "Untitled"')
        fix=$(echo "$finding" | jq -r '.fix // .title // "No fix specified"')

        printf '%s-%03d:%s at %s:%s\n' "$sev" "$task_num" "$fix" "$file" "$line" >> "$TASKS_TXT"
        task_num=$((task_num + 1))
    done < <(echo "$verified" | jq -c '.[]')

    TASK_COUNT=$((task_num - 1))
    log_info "Generated $TASK_COUNT tasks: $TASKS_TXT"
}

#######################################
# Summary
#######################################
print_summary() {
    echo ""
    echo "========================================"
    echo "Review Complete"
    echo "========================================"
    echo ""
    echo "Statistics:"
    echo "  Files Reviewed:      ${CHANGED_FILE_COUNT:-0}"
    echo "  Discovery Batches:   ${NUM_BATCHES:-0}"
    echo "  Findings Discovered: ${DISCOVERY_COUNT:-0}"
    echo "  Findings Verified:   ${VERIFIED_COUNT:-0}"
    echo "  Findings Rejected:   ${REJECTED_COUNT:-0}"
    echo "  Tasks Generated:     ${TASK_COUNT:-0}"
    echo ""
    echo "Opus Review:"
    echo "  Overrides:           ${OPUS_OVERRIDES:-0}"
    echo ""
    echo "Job Execution:"
    echo "  Total Jobs:          ${CUMULATIVE_TOTAL_JOBS:-0}"
    echo "  Failed Jobs:         ${CUMULATIVE_FAILED_JOBS:-0}"
    if [[ ${CUMULATIVE_FAILED_JOBS:-0} -gt 0 ]]; then
        echo "  WARNING: Some background jobs failed - results may be incomplete"
    fi
    echo ""

    # Print hallucination defense stats
    print_hallucination_stats

    # Calculate final cost total
    TOTAL_COST=$(calculate_total_cost)

    echo "Cost:"
    echo "  Total:               \$${TOTAL_COST}"
    echo "  Budget:              \$${MAX_TOTAL_BUDGET}"
    echo ""
    echo "Outputs:"
    echo "  $REVIEW_MD"
    echo "  $TASKS_TXT"
    echo ""
    echo "========================================"
}

#######################################
# Main
#######################################
main() {
    local config_file=""
    local calibrate_mode=""
    local calibrate_dir=""

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --config)
                config_file="$2"
                shift 2
                ;;
            --debug)
                DEBUG=true
                shift
                ;;
            --json-log)
                LOG_FORMAT=json
                shift
                ;;
            --no-quote-verify)
                ENABLE_QUOTE_VERIFICATION=false
                shift
                ;;
            --no-reloc)
                ENABLE_RELOCALIZATION=false
                shift
                ;;
            --no-self-consistency)
                ENABLE_SELF_CONSISTENCY=false
                shift
                ;;
            --calibrate)
                calibrate_mode="run"
                calibrate_dir="${2:-.claude/calibration}"
                shift
                [[ "$1" != -* ]] && { calibrate_dir="$1"; shift; }
                ;;
            --generate-calibration)
                calibrate_mode="generate"
                calibrate_dir="${2:-.claude/calibration}"
                shift
                [[ "$1" != -* ]] && { calibrate_dir="$1"; shift; }
                ;;
            --help|-h)
                echo "Usage: $0 [base_branch] [options]"
                echo ""
                echo "Options:"
                echo "  --config <file>        Load config from JSON file"
                echo "  --debug                Enable debug logging"
                echo "  --json-log             Output logs in JSON format"
                echo "  --no-quote-verify      Disable quote verification layer"
                echo "  --no-reloc             Disable re-localization layer"
                echo "  --no-self-consistency  Disable self-consistency (multi-run) check"
                echo "  --calibrate [dir]      Run calibration suite (default: .claude/calibration)"
                echo "  --generate-calibration [dir]  Generate sample calibration data"
                echo "  --help, -h             Show this help"
                exit 0
                ;;
            *)
                BASE_BRANCH="$1"
                shift
                ;;
        esac
    done

    check_prerequisites
    init_defaults "${BASE_BRANCH:-main}"

    if [[ -n "$config_file" ]]; then
        load_config "$config_file"
    elif [[ -f ".claude/orchestrator.json" ]]; then
        load_config ".claude/orchestrator.json"
    fi

    # Handle calibration modes
    if [[ "$calibrate_mode" == "generate" ]]; then
        if type generate_sample_calibration_data &>/dev/null; then
            generate_sample_calibration_data "$calibrate_dir"
            exit $EXIT_SUCCESS
        else
            log_error "Calibration library not loaded"
            exit $EXIT_ERROR
        fi
    fi

    if [[ "$calibrate_mode" == "run" ]]; then
        if type run_calibration &>/dev/null; then
            # Set up minimal work dir for calibration
            WORK_DIR=$(mktemp -d)
            init_cost_tracking
            init_hallucination_stats "$WORK_DIR"

            if run_calibration "$calibrate_dir"; then
                rm -rf "$WORK_DIR"
                exit $EXIT_SUCCESS
            else
                rm -rf "$WORK_DIR"
                exit $EXIT_FINDINGS_FOUND
            fi
        else
            log_error "Calibration library not loaded"
            exit $EXIT_ERROR
        fi
    fi

    # Run phases
    phase_setup
    phase_build_index
    phase_discovery
    phase_quote_verification
    phase_relocalization
    phase_build_context
    phase_verification
    phase_opus_review
    phase_generate_report
    phase_extract_tasks
    print_summary

    if [[ "${VERIFIED_COUNT:-0}" -gt 0 ]]; then
        exit $EXIT_FINDINGS_FOUND
    else
        exit $EXIT_SUCCESS
    fi
}

main "$@"
