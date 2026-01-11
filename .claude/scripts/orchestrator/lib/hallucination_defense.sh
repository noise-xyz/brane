#!/bin/bash
# Hallucination Defense Library for Code Review Orchestrator v7
#
# Provides three layers of defense against LLM hallucinations:
#   1. Enumerated Choices - constrain file/line to valid indices
#   2. Semantic Anchoring - verify code quotes against source
#   3. Bidirectional Validation - independent re-localization
#
# Usage: source this file from orchestrator_v7.sh

#######################################
# Layer 1: Enumerated Choices
#######################################

# Build enumerated file index
# Creates FILE_INDEX array and FILE_INDEX_MAP for bidirectional lookup
# Args: $1 = file containing list of files (one per line)
# Output: Sets FILE_INDEX[@] and NUM_FILES
build_file_index() {
    local files_list="$1"
    declare -g -a FILE_INDEX=()
    declare -g NUM_FILES=0

    local idx=1
    while IFS= read -r file_path; do
        [[ -z "$file_path" ]] && continue
        [[ ! -f "$file_path" ]] && continue
        FILE_INDEX[$idx]="$file_path"
        idx=$((idx + 1))
    done < "$files_list"

    NUM_FILES=$((idx - 1))
    log_debug "Built file index with $NUM_FILES files"
}

# Format file content with bracketed line numbers
# Args: $1 = file path
# Output: stdout with [NNN] prefixed lines
format_file_with_line_numbers() {
    local file="$1"
    local max_lines="${2:-9999}"

    if [[ ! -f "$file" ]]; then
        echo "[File not found: $file]"
        return 1
    fi

    awk -v max="$max_lines" '
        NR <= max {
            printf "[%03d] %s\n", NR, $0
        }
        NR == max + 1 {
            printf "[...] (truncated at %d lines)\n", max
        }
    ' "$file"
}

# Build enumerated prompt header
# Args: None (uses FILE_INDEX global)
# Output: stdout with formatted file list
build_enumerated_header() {
    echo "## Files Under Review"
    echo ""
    local idx
    for idx in "${!FILE_INDEX[@]}"; do
        local file="${FILE_INDEX[$idx]}"
        local line_count
        line_count=$(wc -l < "$file" 2>/dev/null | tr -d ' ' || echo "0")
        local short_path="${file#*/src/main/java/}"
        echo "[$idx] $short_path ($line_count lines)"
    done
    echo ""
}

# Validate finding indices are in range
# Args: $1 = file_index, $2 = line_index
# Returns: 0 if valid, 1 if invalid
validate_indices() {
    local file_idx="$1"
    local line_idx="$2"

    # Validate file index
    if [[ -z "$file_idx" ]] || ! [[ "$file_idx" =~ ^[0-9]+$ ]]; then
        echo "INVALID:file_index_not_numeric"
        return 1
    fi

    if [[ "$file_idx" -lt 1 ]] || [[ "$file_idx" -gt "$NUM_FILES" ]]; then
        echo "INVALID:file_index_out_of_range:$file_idx"
        return 1
    fi

    # Validate line index
    if [[ -z "$line_idx" ]] || ! [[ "$line_idx" =~ ^[0-9]+$ ]]; then
        echo "INVALID:line_index_not_numeric"
        return 1
    fi

    local file="${FILE_INDEX[$file_idx]}"
    local max_lines
    max_lines=$(wc -l < "$file" 2>/dev/null | tr -d ' ')

    if [[ "$line_idx" -lt 1 ]] || [[ "$line_idx" -gt "$max_lines" ]]; then
        echo "INVALID:line_index_out_of_range:$line_idx:max=$max_lines"
        return 1
    fi

    echo "VALID"
    return 0
}

# Resolve indices to actual file path and line
# Args: $1 = file_index, $2 = line_index
# Output: "file_path|line_number" or empty on error
resolve_indices() {
    local file_idx="$1"
    local line_idx="$2"

    if [[ -z "${FILE_INDEX[$file_idx]:-}" ]]; then
        return 1
    fi

    echo "${FILE_INDEX[$file_idx]}|$line_idx"
}

#######################################
# Layer 2: Semantic Anchoring
#######################################

# Verify code quote exists at claimed location
# Args: $1 = file path, $2 = claimed line, $3 = code quote
# Output: "MATCH:type:actual_line" or "NO_MATCH"
verify_quote() {
    local file="$1"
    local claimed_line="$2"
    local quote="$3"

    if [[ ! -f "$file" ]]; then
        echo "NO_MATCH:file_not_found"
        return 1
    fi

    if [[ -z "$quote" ]] || [[ ${#quote} -lt 5 ]]; then
        echo "NO_MATCH:quote_too_short"
        return 1
    fi

    # Normalize whitespace for comparison
    local normalized_quote
    normalized_quote=$(echo "$quote" | tr -s ' \t' ' ' | sed 's/^ *//;s/ *$//')

    # Escape special regex characters for grep
    local escaped_quote
    escaped_quote=$(printf '%s\n' "$normalized_quote" | sed 's/[[\.*^$()+?{|]/\\&/g')

    # Strategy 1: Exact line match
    local actual_line
    actual_line=$(sed -n "${claimed_line}p" "$file" 2>/dev/null | tr -s ' \t' ' ' | sed 's/^ *//;s/ *$//')

    if [[ -n "$actual_line" ]] && [[ "$actual_line" == *"$normalized_quote"* ]]; then
        echo "MATCH:exact:$claimed_line"
        return 0
    fi

    # Strategy 2: Fuzzy window search (±5 lines)
    local offset
    for offset in 1 -1 2 -2 3 -3 4 -4 5 -5; do
        local check_line=$((claimed_line + offset))
        [[ "$check_line" -lt 1 ]] && continue

        local line_content
        line_content=$(sed -n "${check_line}p" "$file" 2>/dev/null | tr -s ' \t' ' ' | sed 's/^ *//;s/ *$//')

        if [[ -n "$line_content" ]] && [[ "$line_content" == *"$normalized_quote"* ]]; then
            echo "MATCH:nearby:$check_line"
            return 0
        fi
    done

    # Strategy 3: Global search in file (exact quote)
    local found_line
    found_line=$(grep -n -F "$normalized_quote" "$file" 2>/dev/null | head -1 | cut -d: -f1)

    if [[ -n "$found_line" ]]; then
        echo "MATCH:elsewhere:$found_line"
        return 0
    fi

    # Strategy 4: Partial match - STRICT requirements to avoid false positives
    # Only attempt partial match if:
    #   1. Quote is substantial (>80 chars - a full statement)
    #   2. We match at least 80 chars (not just common prefixes)
    #   3. The partial is unique in the file (only 1 match)
    if [[ ${#normalized_quote} -gt 80 ]]; then
        local partial="${normalized_quote:0:80}"
        local match_count
        match_count=$(grep -c -F "$partial" "$file" 2>/dev/null || echo "0")

        # Only accept if exactly one match (unique)
        if [[ "$match_count" -eq 1 ]]; then
            found_line=$(grep -n -F "$partial" "$file" 2>/dev/null | head -1 | cut -d: -f1)
            if [[ -n "$found_line" ]]; then
                echo "MATCH:partial:$found_line"
                return 0
            fi
        fi
    fi

    # Strategy 5: Token-based matching for very short quotes
    # If the quote looks like an identifier or method call, search for it as a token
    # This handles cases where whitespace normalization differs
    if [[ ${#normalized_quote} -ge 10 ]] && [[ ${#normalized_quote} -le 60 ]]; then
        # Extract the core token (alphanumeric + underscores)
        local token
        token=$(echo "$normalized_quote" | grep -oE '[a-zA-Z_][a-zA-Z0-9_]*\s*\(' | head -1)
        if [[ -n "$token" ]]; then
            local token_matches
            token_matches=$(grep -n -F "$token" "$file" 2>/dev/null | wc -l | tr -d ' ')
            # Only accept if very few matches (likely unique method)
            if [[ "$token_matches" -eq 1 ]]; then
                found_line=$(grep -n -F "$token" "$file" 2>/dev/null | head -1 | cut -d: -f1)
                if [[ -n "$found_line" ]]; then
                    echo "MATCH:token:$found_line"
                    return 0
                fi
            fi
        fi
    fi

    echo "NO_MATCH"
    return 1
}

# Process quote verification for a finding
# Args: $1 = finding JSON
# Output: finding JSON with quote_status and corrected_line fields
process_quote_verification() {
    local finding="$1"

    local file line quote
    file=$(echo "$finding" | jq -r '.file // empty')
    line=$(echo "$finding" | jq -r '.line // 0')
    quote=$(echo "$finding" | jq -r '.code_quote // empty')

    local result
    result=$(verify_quote "$file" "$line" "$quote")

    local status actual_line
    status="${result%%:*}"

    if [[ "$status" == "MATCH" ]]; then
        local match_type="${result#MATCH:}"
        local match_kind="${match_type%%:*}"
        actual_line="${match_type#*:}"

        echo "$finding" | jq --arg status "$match_kind" --argjson actual "$actual_line" \
            '. + {quote_status: $status, corrected_line: $actual}'
    else
        echo "$finding" | jq --arg status "rejected" --arg reason "$result" \
            '. + {quote_status: $status, rejection_reason: $reason}'
    fi
}

#######################################
# Layer 3: Bidirectional Validation
#######################################

# Build re-localization prompt (INDEPENDENT - no description to avoid confirmation bias)
# Args: $1 = file path, $2 = category, $3 = description (unused - kept for API compat)
# Output: prompt string
build_reloc_prompt() {
    local file="$1"
    local category="$2"
    # NOTE: $3 (description) intentionally unused to prevent confirmation bias

    local file_content
    file_content=$(format_file_with_line_numbers "$file" 500)

    # Map category to search instructions (without revealing the specific issue)
    local search_instructions
    case "$category" in
        NULL_SAFETY)
            search_instructions="Find lines where a variable could be null when dereferenced, or where Optional.get() is called without isPresent() check."
            ;;
        RESOURCE)
            search_instructions="Find lines where resources (streams, connections, locks) may not be properly closed or released."
            ;;
        THREAD)
            search_instructions="Find lines with potential thread-safety issues: unsynchronized access to shared mutable state, race conditions."
            ;;
        TYPE)
            search_instructions="Find lines with raw type usage, unchecked casts, or type safety violations."
            ;;
        EXCEPTION)
            search_instructions="Find lines where exceptions are swallowed (empty catch), or where checked exceptions are improperly handled."
            ;;
        PATTERN)
            search_instructions="Find lines with anti-patterns: long if-else chains that should be switch, instanceof without pattern matching, string concat in loops."
            ;;
        *)
            search_instructions="Find lines with potential bugs or code quality issues."
            ;;
    esac

    cat << EOF
You are an independent code reviewer. Your task is to find ALL potential $category issues in this file.

## What to Look For
$search_instructions

## Instructions
1. Scan the entire file for issues matching the category above
2. For EACH issue found, report the line number
3. Return found=true with the line number of the MOST SEVERE issue
4. If no issues of this category exist, return found=false

IMPORTANT:
- This is an INDEPENDENT review - find issues based on the code alone
- Use the bracketed line numbers [NNN] shown in the code
- Only report real issues, not style preferences
- If multiple issues exist, report the one most likely to cause a bug

## Code
[File: $file]
\`\`\`java
$file_content
\`\`\`
EOF
}

# Compare discovery location with re-localization result
# Args: $1 = discovery line, $2 = reloc line (or "null"), $3 = tolerance (default 3)
#       $4 = discovery description (optional), $5 = reloc issue_type (optional)
# Output: "AGREE" | "AGREE_WEAK" | "DISAGREE:discovery=N,reloc=M" | "NOT_FOUND" | "SEMANTIC_MISMATCH"
compare_locations() {
    local discovery_line="$1"
    local reloc_line="$2"
    local tolerance="${3:-3}"
    local discovery_desc="${4:-}"
    local reloc_issue_type="${5:-}"

    # Handle not found
    if [[ -z "$reloc_line" ]] || [[ "$reloc_line" == "null" ]] || [[ "$reloc_line" == "0" ]]; then
        echo "NOT_FOUND"
        return
    fi

    # Calculate difference
    local diff=$((discovery_line - reloc_line))
    diff=${diff#-}  # Absolute value

    local location_agrees=false
    if [[ "$diff" -le "$tolerance" ]]; then
        location_agrees=true
    fi

    # If we have semantic info, check it
    if [[ -n "$discovery_desc" ]] && [[ -n "$reloc_issue_type" ]]; then
        local semantic_match
        semantic_match=$(check_semantic_similarity "$discovery_desc" "$reloc_issue_type")

        if [[ "$location_agrees" == "true" ]]; then
            if [[ "$semantic_match" == "true" ]]; then
                echo "AGREE"
            else
                # Location matches but different issue type - suspicious
                echo "AGREE_WEAK:semantic_mismatch"
            fi
        else
            if [[ "$semantic_match" == "true" ]]; then
                # Same issue type but different location - could be legitimate
                echo "DISAGREE:discovery=$discovery_line,reloc=$reloc_line,same_type"
            else
                echo "DISAGREE:discovery=$discovery_line,reloc=$reloc_line"
            fi
        fi
    else
        # No semantic info, fall back to location-only comparison
        if [[ "$location_agrees" == "true" ]]; then
            echo "AGREE"
        else
            echo "DISAGREE:discovery=$discovery_line,reloc=$reloc_line"
        fi
    fi
}

# Check if discovery description and reloc issue_type refer to similar issues
# Args: $1 = discovery description, $2 = reloc issue_type
# Output: "true" or "false"
check_semantic_similarity() {
    local discovery_desc="$1"
    local reloc_type="$2"

    # Normalize to lowercase
    discovery_desc=$(echo "$discovery_desc" | tr '[:upper:]' '[:lower:]')
    reloc_type=$(echo "$reloc_type" | tr '[:upper:]' '[:lower:]')

    # Define issue type keywords for matching
    # Null-related
    if [[ "$reloc_type" == *"null"* ]] || [[ "$reloc_type" == *"npe"* ]]; then
        if [[ "$discovery_desc" == *"null"* ]] || [[ "$discovery_desc" == *"optional"* ]] || [[ "$discovery_desc" == *"npe"* ]]; then
            echo "true"
            return
        fi
    fi

    # Resource-related
    if [[ "$reloc_type" == *"resource"* ]] || [[ "$reloc_type" == *"close"* ]] || [[ "$reloc_type" == *"leak"* ]]; then
        if [[ "$discovery_desc" == *"resource"* ]] || [[ "$discovery_desc" == *"close"* ]] || [[ "$discovery_desc" == *"leak"* ]] || [[ "$discovery_desc" == *"stream"* ]]; then
            echo "true"
            return
        fi
    fi

    # Thread-related
    if [[ "$reloc_type" == *"thread"* ]] || [[ "$reloc_type" == *"race"* ]] || [[ "$reloc_type" == *"sync"* ]] || [[ "$reloc_type" == *"concurrent"* ]]; then
        if [[ "$discovery_desc" == *"thread"* ]] || [[ "$discovery_desc" == *"race"* ]] || [[ "$discovery_desc" == *"sync"* ]] || [[ "$discovery_desc" == *"concurrent"* ]] || [[ "$discovery_desc" == *"shared"* ]]; then
            echo "true"
            return
        fi
    fi

    # Exception-related
    if [[ "$reloc_type" == *"exception"* ]] || [[ "$reloc_type" == *"catch"* ]] || [[ "$reloc_type" == *"swallow"* ]]; then
        if [[ "$discovery_desc" == *"exception"* ]] || [[ "$discovery_desc" == *"catch"* ]] || [[ "$discovery_desc" == *"swallow"* ]] || [[ "$discovery_desc" == *"error"* ]]; then
            echo "true"
            return
        fi
    fi

    # Type-related
    if [[ "$reloc_type" == *"type"* ]] || [[ "$reloc_type" == *"cast"* ]] || [[ "$reloc_type" == *"raw"* ]]; then
        if [[ "$discovery_desc" == *"type"* ]] || [[ "$discovery_desc" == *"cast"* ]] || [[ "$discovery_desc" == *"raw"* ]] || [[ "$discovery_desc" == *"generic"* ]]; then
            echo "true"
            return
        fi
    fi

    # If reloc_type contains any word from discovery_desc (5+ chars), consider it a match
    local word
    for word in $discovery_desc; do
        if [[ ${#word} -ge 5 ]] && [[ "$reloc_type" == *"$word"* ]]; then
            echo "true"
            return
        fi
    done

    echo "false"
}

#######################################
# Semantic Deduplication
#######################################

# Deduplicate findings considering semantic similarity
# Two findings are duplicates if:
#   1. Same file AND
#   2. Lines within tolerance (default 5) AND
#   3. Same category OR similar descriptions
#
# Args: $1 = input jsonl file, $2 = output file, $3 = line tolerance (default 5)
deduplicate_findings_semantic() {
    local input_file="$1"
    local output_file="$2"
    local line_tolerance="${3:-5}"

    if [[ ! -f "$input_file" ]] || [[ ! -s "$input_file" ]]; then
        : > "$output_file"
        return
    fi

    # Use jq for accurate deduplication
    jq -s --argjson tol "$line_tolerance" '
        # Group by file
        group_by(.file) |
        map(
            # Within each file, sort by line and dedupe nearby same-category
            sort_by(.line) |
            reduce .[] as $item (
                [];
                if length == 0 then
                    [$item]
                else
                    # Check if this is a duplicate of the last item
                    ((.[-1].line - $item.line) | if . < 0 then -. else . end) as $diff |
                    if $diff <= $tol and .[-1].category == $item.category then
                        # Duplicate - keep higher severity
                        if (["T1","T2","T3","T4"] | index($item.severity)) < (["T1","T2","T3","T4"] | index(.[-1].severity)) then
                            .[:-1] + [$item]
                        else
                            .
                        end
                    else
                        . + [$item]
                    end
                end
            )
        ) |
        flatten
    ' "$input_file" > "$output_file"
}

# Deduplicate JSON array with semantic similarity
# Args: $1 = JSON array (as string or via stdin)
# Output: deduplicated JSON array to stdout
deduplicate_json_array_semantic() {
    local tolerance="${1:-5}"

    jq --argjson tol "$tolerance" '
        # Group by file
        group_by(.file) |
        map(
            # Within each file, sort by line and dedupe nearby same-category
            sort_by(.line) |
            reduce .[] as $item (
                [];
                if length == 0 then
                    [$item]
                else
                    ((.[-1].line - $item.line) | if . < 0 then -. else . end) as $diff |
                    if $diff <= $tol and .[-1].category == $item.category then
                        # Keep higher severity
                        if (["T1","T2","T3","T4"] | index($item.severity) // 4) < (["T1","T2","T3","T4"] | index(.[-1].severity) // 4) then
                            .[:-1] + [$item]
                        else
                            .
                        end
                    else
                        . + [$item]
                    end
                end
            )
        ) |
        flatten
    '
}

#######################################
# Metrics Tracking (File-Based for Subshell Safety)
#######################################

# Stats file path - set by init function
HALLUCINATION_STATS_FILE=""

# Initialize file-based stats tracking
# Args: $1 = work directory
init_hallucination_stats() {
    local work_dir="$1"
    HALLUCINATION_STATS_FILE="$work_dir/hallucination_stats.log"
    : > "$HALLUCINATION_STATS_FILE"  # Create/truncate
}

reset_hallucination_stats() {
    if [[ -n "$HALLUCINATION_STATS_FILE" ]] && [[ -f "$HALLUCINATION_STATS_FILE" ]]; then
        : > "$HALLUCINATION_STATS_FILE"
    fi
}

# Increment stat using file-based atomic append (safe from subshells)
# Args: $1 = stat name
increment_stat() {
    local stat_name="$1"

    if [[ -z "$HALLUCINATION_STATS_FILE" ]]; then
        log_debug "Warning: HALLUCINATION_STATS_FILE not set, stat '$stat_name' lost"
        return
    fi

    # Atomic append - each stat is one line
    echo "$stat_name" >> "$HALLUCINATION_STATS_FILE"
}

# Read stats from file and compute totals
# Returns associative array style output
_compute_stats() {
    if [[ ! -f "$HALLUCINATION_STATS_FILE" ]]; then
        echo "0 0 0 0 0 0 0 0"
        return
    fi

    local invalid_index=0
    local quote_rejected=0
    local quote_corrected=0
    local reloc_not_found=0
    local reloc_disagree=0
    local semantic_mismatch=0
    local self_consistency_rejected=0
    local passed=0

    while IFS= read -r stat_name; do
        case "$stat_name" in
            invalid_index)              invalid_index=$((invalid_index + 1)) ;;
            quote_rejected)             quote_rejected=$((quote_rejected + 1)) ;;
            quote_corrected)            quote_corrected=$((quote_corrected + 1)) ;;
            reloc_not_found)            reloc_not_found=$((reloc_not_found + 1)) ;;
            reloc_disagree)             reloc_disagree=$((reloc_disagree + 1)) ;;
            semantic_mismatch)          semantic_mismatch=$((semantic_mismatch + 1)) ;;
            self_consistency_rejected)  self_consistency_rejected=$((self_consistency_rejected + 1)) ;;
            passed)                     passed=$((passed + 1)) ;;
        esac
    done < "$HALLUCINATION_STATS_FILE"

    echo "$invalid_index $quote_rejected $quote_corrected $reloc_not_found $reloc_disagree $semantic_mismatch $self_consistency_rejected $passed"
}

print_hallucination_stats() {
    local stats
    stats=$(_compute_stats)

    local invalid_index quote_rejected quote_corrected reloc_not_found reloc_disagree semantic_mismatch self_consistency_rejected passed
    read -r invalid_index quote_rejected quote_corrected reloc_not_found reloc_disagree semantic_mismatch self_consistency_rejected passed <<< "$stats"

    local total=$((invalid_index + quote_rejected + reloc_not_found + reloc_disagree + self_consistency_rejected + passed))

    echo ""
    echo "Hallucination Defense Stats:"
    echo "  Layer 0 (Self-Consistency):"
    echo "    Inconsistent findings:     $self_consistency_rejected"
    echo "  Layer 1 (Index Validation):"
    echo "    Invalid indices rejected:  $invalid_index"
    echo "  Layer 2 (Quote Verification):"
    echo "    Quote mismatch rejected:   $quote_rejected"
    echo "    Line corrections made:     $quote_corrected"
    echo "  Layer 3 (Re-localization):"
    echo "    Re-loc not found:          $reloc_not_found"
    echo "    Re-loc disagreed:          $reloc_disagree"
    echo "    Semantic mismatch (weak):  $semantic_mismatch"
    echo "  Passed all layers:           $passed"
    echo ""

    if [[ $total -gt 0 ]]; then
        local rejected=$((total - passed))
        local pct=$((rejected * 100 / total))
        echo "  Hallucination catch rate:    ${pct}% ($rejected / $total)"
    fi
}
