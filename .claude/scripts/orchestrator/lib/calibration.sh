#!/bin/bash
# Calibration Framework for Code Review Orchestrator
#
# Measures precision/recall against known test cases:
#   - True Positives: Files with known bugs that SHOULD be flagged
#   - True Negatives: Clean files that should NOT produce findings
#
# Usage:
#   source this file from orchestrator
#   run_calibration <calibration_data_dir>
#
# Calibration Data Structure:
#   calibration/
#     true_positives/
#       case_001/
#         file.java          # File with known bug
#         expected.json      # Expected finding(s)
#       case_002/
#         ...
#     true_negatives/
#       clean_001.java       # Clean file (no findings expected)
#       clean_002.java
#

#######################################
# Expected Finding Schema
#######################################
# expected.json format:
# {
#   "findings": [
#     {
#       "line": 42,
#       "line_tolerance": 3,
#       "category": "NULL_SAFETY",
#       "severity_min": "T2",
#       "description_contains": ["null", "deref"]
#     }
#   ]
# }

#######################################
# Calibration Runner
#######################################

# Run calibration against test cases
# Args: $1 = calibration data directory
# Output: calibration report to stdout, metrics to calibration_results.json
run_calibration() {
    local calibration_dir="$1"
    local results_file="${2:-calibration_results.json}"

    if [[ ! -d "$calibration_dir" ]]; then
        log_error "Calibration directory not found: $calibration_dir"
        return 1
    fi

    log_info "========================================"
    log_info "Running Calibration Suite"
    log_info "========================================"

    local tp_detected=0
    local tp_total=0
    local fp_count=0
    local tn_count=0
    local fn_count=0

    # Process True Positive cases
    local tp_dir="$calibration_dir/true_positives"
    if [[ -d "$tp_dir" ]]; then
        log_info "Processing True Positive cases..."
        for case_dir in "$tp_dir"/*/; do
            [[ ! -d "$case_dir" ]] && continue

            local case_name
            case_name=$(basename "$case_dir")
            local expected_file="$case_dir/expected.json"

            if [[ ! -f "$expected_file" ]]; then
                log_warn "Skipping $case_name: no expected.json"
                continue
            fi

            # Find Java file(s) in case directory
            local java_files
            java_files=$(find "$case_dir" -name "*.java" -type f)

            if [[ -z "$java_files" ]]; then
                log_warn "Skipping $case_name: no Java files"
                continue
            fi

            # Run discovery on this case
            local findings
            findings=$(run_discovery_on_files "$java_files")

            # Compare against expected
            local result
            result=$(compare_findings_to_expected "$findings" "$expected_file")
            local matched="${result%%|*}"
            local expected="${result##*|}"

            tp_total=$((tp_total + expected))

            if [[ "$matched" -gt 0 ]]; then
                tp_detected=$((tp_detected + matched))
                log_info "  ✓ $case_name: $matched/$expected expected findings detected"
            else
                fn_count=$((fn_count + expected))
                log_warn "  ✗ $case_name: 0/$expected expected findings detected (FALSE NEGATIVE)"
            fi
        done
    fi

    # Process True Negative cases
    local tn_dir="$calibration_dir/true_negatives"
    if [[ -d "$tn_dir" ]]; then
        log_info "Processing True Negative cases..."
        for java_file in "$tn_dir"/*.java; do
            [[ ! -f "$java_file" ]] && continue

            local file_name
            file_name=$(basename "$java_file")

            # Run discovery on this file
            local findings
            findings=$(run_discovery_on_files "$java_file")
            local finding_count
            finding_count=$(echo "$findings" | jq 'length')

            if [[ "$finding_count" -eq 0 ]]; then
                tn_count=$((tn_count + 1))
                log_info "  ✓ $file_name: No findings (correct)"
            else
                fp_count=$((fp_count + finding_count))
                log_warn "  ✗ $file_name: $finding_count findings (FALSE POSITIVES)"

                # Log the false positives for analysis
                echo "$findings" | jq -c '.[]' | while read -r finding; do
                    local line category desc
                    line=$(echo "$finding" | jq -r '.line')
                    category=$(echo "$finding" | jq -r '.category')
                    desc=$(echo "$finding" | jq -r '.description')
                    log_debug "    FP: L$line [$category] $desc"
                done
            fi
        done
    fi

    # Calculate metrics
    local precision recall f1
    local total_positives=$((tp_detected + fp_count))
    local total_actual=$((tp_detected + fn_count))

    if [[ $total_positives -gt 0 ]]; then
        precision=$(echo "scale=4; $tp_detected / $total_positives" | bc)
    else
        precision="1.0000"
    fi

    if [[ $total_actual -gt 0 ]]; then
        recall=$(echo "scale=4; $tp_detected / $total_actual" | bc)
    else
        recall="1.0000"
    fi

    local precision_recall_sum
    precision_recall_sum=$(echo "$precision + $recall" | bc)
    if [[ $(echo "$precision_recall_sum > 0" | bc) -eq 1 ]]; then
        f1=$(echo "scale=4; 2 * $precision * $recall / $precision_recall_sum" | bc)
    else
        f1="0.0000"
    fi

    # Output results
    echo ""
    echo "========================================"
    echo "Calibration Results"
    echo "========================================"
    echo ""
    echo "True Positives Detected:  $tp_detected / $tp_total"
    echo "False Positives:          $fp_count"
    echo "False Negatives:          $fn_count"
    echo "True Negatives:           $tn_count"
    echo ""
    echo "Precision:                $precision"
    echo "Recall:                   $recall"
    echo "F1 Score:                 $f1"
    echo ""

    # Save results to JSON
    cat > "$results_file" << EOF
{
    "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
    "true_positives_detected": $tp_detected,
    "true_positives_total": $tp_total,
    "false_positives": $fp_count,
    "false_negatives": $fn_count,
    "true_negatives": $tn_count,
    "precision": $precision,
    "recall": $recall,
    "f1_score": $f1
}
EOF

    log_info "Results saved to: $results_file"

    # Return success if F1 > 0.7 (reasonable threshold)
    if [[ $(echo "$f1 > 0.7" | bc) -eq 1 ]]; then
        return 0
    else
        log_warn "F1 score below threshold (0.7)"
        return 1
    fi
}

# Run discovery on specific files (minimal version for calibration)
# Args: $1 = space-separated list of Java files
# Output: JSON array of findings
run_discovery_on_files() {
    local files="$1"
    local temp_dir
    temp_dir=$(mktemp -d)

    # Create file index
    local idx=1
    local file_list=""
    for file in $files; do
        echo "$file" >> "$temp_dir/files.txt"
        idx=$((idx + 1))
    done

    # Build minimal file index
    build_file_index "$temp_dir/files.txt"
    build_enumerated_header > "$temp_dir/file_header.txt"

    # Build content
    local content=""
    for idx in "${!FILE_INDEX[@]}"; do
        local file="${FILE_INDEX[$idx]}"
        [[ ! -f "$file" ]] && continue
        local file_content
        file_content=$(format_file_with_line_numbers "$file" 500)
        content+="
### [$idx] $(basename "$file")
\`\`\`java
$file_content
\`\`\`
"
    done

    # Run discovery
    local prompt
    prompt=$(build_discovery_prompt_v7 "$content" "calibration")

    local response
    if response=$(claude_call "$prompt" "$DISCOVERY_SCHEMA_V7" "$MODEL_DISCOVERY"); then
        local findings
        findings=$(echo "$response" | jq -r '.structured_output.findings // []')

        # Resolve indices to paths
        local resolved='[]'
        while IFS= read -r finding; do
            [[ -z "$finding" ]] || [[ "$finding" == "null" ]] && continue

            local file_idx line_idx
            file_idx=$(echo "$finding" | jq -r '.file_index // 0')
            line_idx=$(echo "$finding" | jq -r '.line_index // 0')

            local validation
            validation=$(validate_indices "$file_idx" "$line_idx")
            if [[ "$validation" == "VALID" ]]; then
                local res
                res=$(resolve_indices "$file_idx" "$line_idx")
                local file_path="${res%%|*}"
                local line_num="${res##*|}"

                local resolved_finding
                resolved_finding=$(echo "$finding" | jq -c \
                    --arg file "$file_path" \
                    --argjson line "$line_num" \
                    '. + {file: $file, line: $line}')
                resolved=$(echo "$resolved" | jq --argjson f "$resolved_finding" '. + [$f]')
            fi
        done < <(echo "$findings" | jq -c '.[]')

        echo "$resolved"
    else
        echo '[]'
    fi

    rm -rf "$temp_dir"
}

# Compare actual findings against expected
# Args: $1 = findings JSON array, $2 = expected.json file path
# Output: "matched|total" counts
compare_findings_to_expected() {
    local findings="$1"
    local expected_file="$2"

    local expected_findings
    expected_findings=$(jq '.findings // []' "$expected_file")
    local expected_count
    expected_count=$(echo "$expected_findings" | jq 'length')

    local matched=0

    while IFS= read -r expected; do
        [[ -z "$expected" ]] || [[ "$expected" == "null" ]] && continue

        local exp_line exp_tolerance exp_category exp_severity_min
        exp_line=$(echo "$expected" | jq -r '.line')
        exp_tolerance=$(echo "$expected" | jq -r '.line_tolerance // 3')
        exp_category=$(echo "$expected" | jq -r '.category // ""')
        exp_severity_min=$(echo "$expected" | jq -r '.severity_min // "T4"')

        # Check if any actual finding matches
        local found=false
        while IFS= read -r actual; do
            [[ -z "$actual" ]] || [[ "$actual" == "null" ]] && continue

            local act_line act_category act_severity
            act_line=$(echo "$actual" | jq -r '.line')
            act_category=$(echo "$actual" | jq -r '.category // ""')
            act_severity=$(echo "$actual" | jq -r '.severity // "T4"')

            # Check line proximity
            local diff=$((exp_line - act_line))
            diff=${diff#-}
            if [[ $diff -gt $exp_tolerance ]]; then
                continue
            fi

            # Check category if specified
            if [[ -n "$exp_category" ]] && [[ "$exp_category" != "$act_category" ]]; then
                continue
            fi

            # Check severity meets minimum
            if ! severity_meets_minimum "$act_severity" "$exp_severity_min"; then
                continue
            fi

            found=true
            break
        done < <(echo "$findings" | jq -c '.[]')

        if [[ "$found" == "true" ]]; then
            matched=$((matched + 1))
        fi
    done < <(echo "$expected_findings" | jq -c '.[]')

    echo "$matched|$expected_count"
}

# Check if actual severity meets minimum requirement
# Args: $1 = actual severity, $2 = minimum severity
# Output: returns 0 (true) if meets, 1 (false) otherwise
severity_meets_minimum() {
    local actual="$1"
    local minimum="$2"

    # Convert to numeric (T1=1, T2=2, T3=3, T4=4)
    local actual_num minimum_num
    case "$actual" in
        T1) actual_num=1 ;;
        T2) actual_num=2 ;;
        T3) actual_num=3 ;;
        T4) actual_num=4 ;;
        *) actual_num=5 ;;
    esac

    case "$minimum" in
        T1) minimum_num=1 ;;
        T2) minimum_num=2 ;;
        T3) minimum_num=3 ;;
        T4) minimum_num=4 ;;
        *) minimum_num=4 ;;
    esac

    # Lower number = higher severity, so actual should be <= minimum
    [[ $actual_num -le $minimum_num ]]
}

#######################################
# Sample Calibration Data Generator
#######################################

# Generate sample calibration data structure
# Args: $1 = output directory
generate_sample_calibration_data() {
    local output_dir="$1"

    mkdir -p "$output_dir/true_positives/null_deref_001"
    mkdir -p "$output_dir/true_negatives"

    # Sample true positive: null dereference
    cat > "$output_dir/true_positives/null_deref_001/NullDeref.java" << 'JAVA'
package test;

public class NullDeref {
    public String process(String input) {
        // Bug: input could be null
        return input.toUpperCase();  // Line 6: potential NPE
    }

    public void safeProcess(String input) {
        if (input != null) {
            System.out.println(input.toUpperCase());
        }
    }
}
JAVA

    cat > "$output_dir/true_positives/null_deref_001/expected.json" << 'JSON'
{
    "findings": [
        {
            "line": 6,
            "line_tolerance": 2,
            "category": "NULL_SAFETY",
            "severity_min": "T2",
            "description_contains": ["null"]
        }
    ]
}
JSON

    # Sample true negative: clean code
    cat > "$output_dir/true_negatives/CleanCode.java" << 'JAVA'
package test;

import java.util.Objects;

public class CleanCode {
    public String process(String input) {
        Objects.requireNonNull(input, "input cannot be null");
        return input.toUpperCase();
    }

    public void processOptional(java.util.Optional<String> opt) {
        if (opt.isPresent()) {
            System.out.println(opt.get());
        }
    }
}
JAVA

    log_info "Sample calibration data created in: $output_dir"
    log_info "  - true_positives/null_deref_001/ (NPE test case)"
    log_info "  - true_negatives/CleanCode.java (should have no findings)"
}
