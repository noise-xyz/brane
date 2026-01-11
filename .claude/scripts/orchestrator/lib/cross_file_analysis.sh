#!/bin/bash
# Cross-File Analysis Library for Code Review Orchestrator v7
#
# Provides cross-cutting analysis that spans multiple files:
#   1. Null Propagation - Track null/Optional returns and their handling
#   2. Resource Ownership - Track Closeable/AutoCloseable lifecycle across files
#   3. Contract Violations - Interface promises vs implementation behavior
#   4. Dependency Chain Issues - Transitive dependency problems
#
# This addresses the "No cross-file analysis" gap in the orchestrator assessment.
#
# Usage: source this file from orchestrator

#######################################
# Cross-File Analysis Schemas
#######################################

readonly CROSS_FILE_SCHEMA='{
  "type": "object",
  "properties": {
    "findings": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "issue_type": {"type": "string", "enum": ["NULL_PROPAGATION", "RESOURCE_OWNERSHIP", "CONTRACT_VIOLATION", "DEPENDENCY_CHAIN", "ENCAPSULATION_LEAK", "INCONSISTENT_ERROR_HANDLING"]},
          "severity": {"type": "string", "enum": ["T1", "T2", "T3", "T4"]},
          "source_file": {"type": "string"},
          "source_line": {"type": "integer"},
          "target_file": {"type": "string"},
          "target_line": {"type": "integer"},
          "description": {"type": "string"},
          "chain": {"type": "array", "items": {"type": "string"}, "description": "Files/methods in the propagation chain"},
          "fix_suggestion": {"type": "string"}
        },
        "required": ["issue_type", "severity", "source_file", "source_line", "description"]
      }
    },
    "relationship_health": {
      "type": "object",
      "properties": {
        "clean_boundaries": {"type": "boolean"},
        "concerns": {"type": "array", "items": {"type": "string"}}
      }
    }
  },
  "required": ["findings"]
}'

#######################################
# Brane-Specific Cross-File Knowledge
#######################################

readonly BRANE_CROSS_FILE_PATTERNS='
Cross-File Patterns to Detect:

## Null Propagation (T2-T3)
- Methods returning Optional<T>: Callers must use .orElse(), .orElseThrow(), or .ifPresent()
- Methods returning @Nullable types: Callers must null-check before use
- RPC response parsing: JSON fields may be null, callers must handle

## Resource Ownership (T1-T2)
- PrivateKey: MUST be destroyed by owner; if passed to another class, ownership transfer must be explicit
- InputStream/OutputStream: Closer must be clearly defined when crossing file boundaries
- Connection pools: Lifecycle must be managed by a single owner

## Contract Violations (T2-T3)
- Interface methods throwing exceptions not declared in signature
- Implementations returning null when interface Javadoc says "never returns null"
- Builder patterns: build() should validate all required fields

## Dependency Chains (T3-T4)
- Circular dependencies between packages
- Deep transitive dependencies (A -> B -> C -> D) making testing hard
- Hidden coupling through shared mutable state

## Brane-Specific Rules
- brane-rpc classes must not depend on brane-contract (would create cycle)
- Signer implementations must all handle PrivateKey the same way
- All Wei arithmetic must handle overflow consistently across the codebase
'

#######################################
# Static Relationship Extraction
#######################################

# Extract import graph for a list of files
# Output: JSON array of {file, imports: [package.Class, ...], module}
extract_import_graph() {
    local files_list="$1"
    local output_file="$2"

    local graph='[]'

    while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        [[ ! -f "$file" ]] && continue

        local module
        module=$(echo "$file" | grep -oE 'brane-[a-z]+' | head -1)

        # Extract imports
        local imports
        imports=$(grep "^import " "$file" 2>/dev/null | \
            sed 's/import //; s/static //; s/;$//' | \
            sort -u | \
            jq -R . | jq -s .)

        # Extract package
        local package
        package=$(grep "^package " "$file" 2>/dev/null | \
            sed 's/package //; s/;$//' | head -1)

        # Extract class/interface name
        local class_name
        class_name=$(basename "$file" .java)

        # Check for extends/implements
        local extends_class
        extends_class=$(grep -E "class\s+$class_name\s+extends\s+" "$file" 2>/dev/null | \
            sed -E 's/.*extends\s+([A-Za-z0-9_]+).*/\1/' | head -1)

        local implements_list
        implements_list=$(grep -E "(class|interface)\s+$class_name.*implements\s+" "$file" 2>/dev/null | \
            sed -E 's/.*implements\s+([^{]+).*/\1/' | \
            tr ',' '\n' | sed 's/[[:space:]]//g' | \
            grep -v '^$' | jq -R . | jq -s .)

        graph=$(echo "$graph" | jq --arg f "$file" \
            --arg m "${module:-unknown}" \
            --arg p "$package" \
            --arg c "$class_name" \
            --arg e "$extends_class" \
            --argjson i "$imports" \
            --argjson impl "${implements_list:-[]}" \
            '. + [{
                file: $f,
                module: $m,
                package: $p,
                class: $c,
                extends: (if $e == "" then null else $e end),
                implements: $impl,
                imports: $i
            }]')
    done < "$files_list"

    echo "$graph" > "$output_file"
    log_debug "Import graph extracted: $(echo "$graph" | jq 'length') files"
}

# Extract method signatures (name, return type, parameters)
# Focus on public methods that might be called cross-file
extract_method_signatures() {
    local files_list="$1"
    local output_file="$2"

    local methods='[]'

    while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        [[ ! -f "$file" ]] && continue

        local class_name
        class_name=$(basename "$file" .java)

        # Extract public/protected method signatures
        # Pattern: (public|protected) [static] [<generics>] ReturnType methodName(params)
        local line_num=0
        while IFS= read -r line; do
            line_num=$((line_num + 1))

            # Match method signatures (simplified pattern)
            if [[ "$line" =~ ^[[:space:]]*(public|protected)[[:space:]]+(static[[:space:]]+)?(\<[^\>]+\>[[:space:]]+)?([A-Za-z0-9_\<\>\[\]]+)[[:space:]]+([a-z][A-Za-z0-9_]*)\( ]]; then
                local visibility="${BASH_REMATCH[1]}"
                local is_static="${BASH_REMATCH[2]}"
                local return_type="${BASH_REMATCH[4]}"
                local method_name="${BASH_REMATCH[5]}"

                # Check if return type is nullable indicator
                local nullable=false
                [[ "$return_type" == "Optional"* ]] && nullable="optional"
                [[ "$line" =~ @Nullable ]] && nullable="nullable"

                # Check for resource return types
                local is_resource=false
                [[ "$return_type" =~ (InputStream|OutputStream|Reader|Writer|Connection|Channel|Closeable|AutoCloseable|PrivateKey) ]] && is_resource=true

                methods=$(echo "$methods" | jq --arg f "$file" \
                    --arg c "$class_name" \
                    --argjson l "$line_num" \
                    --arg m "$method_name" \
                    --arg r "$return_type" \
                    --arg n "$nullable" \
                    --argjson res "$is_resource" \
                    '. + [{
                        file: $f,
                        class: $c,
                        line: $l,
                        method: $m,
                        return_type: $r,
                        nullable: (if $n == "false" then false elif $n == "optional" then "optional" else "nullable" end),
                        returns_resource: $res
                    }]')
            fi
        done < "$file"
    done < "$files_list"

    echo "$methods" > "$output_file"
    log_debug "Method signatures extracted: $(echo "$methods" | jq 'length') methods"
}

# Find potential null propagation issues statically
# Look for methods returning Optional/nullable that are called without proper handling
find_null_propagation_candidates() {
    local import_graph="$1"
    local method_sigs="$2"
    local files_list="$3"
    local output_file="$4"

    local candidates='[]'

    # Find methods that return nullable types
    local nullable_methods
    nullable_methods=$(jq '[.[] | select(.nullable != false)]' "$method_sigs")

    local nullable_count
    nullable_count=$(echo "$nullable_methods" | jq 'length')

    if [[ "$nullable_count" -eq 0 ]]; then
        echo '[]' > "$output_file"
        return
    fi

    # For each nullable method, search for callers that don't handle null
    while IFS= read -r method_json; do
        [[ -z "$method_json" ]] || [[ "$method_json" == "null" ]] && continue

        local method_name method_class source_file nullable_type
        method_name=$(echo "$method_json" | jq -r '.method')
        method_class=$(echo "$method_json" | jq -r '.class')
        source_file=$(echo "$method_json" | jq -r '.file')
        nullable_type=$(echo "$method_json" | jq -r '.nullable')

        # Search for callers of this method
        while IFS= read -r caller_file; do
            [[ -z "$caller_file" ]] && continue
            [[ ! -f "$caller_file" ]] && continue
            [[ "$caller_file" == "$source_file" ]] && continue  # Skip self

            # Check if this file might call the method
            local call_pattern="\\.$method_name\\s*\\("
            local call_lines
            call_lines=$(grep -n "$call_pattern" "$caller_file" 2>/dev/null || true)

            if [[ -n "$call_lines" ]]; then
                while IFS=: read -r line_num line_content; do
                    # Check if the call result is properly handled
                    local is_handled=false

                    if [[ "$nullable_type" == "optional" ]]; then
                        # Optional: should see .orElse, .orElseThrow, .ifPresent, .map, .flatMap, .isEmpty, .isPresent
                        if [[ "$line_content" =~ \.(orElse|orElseThrow|ifPresent|map|flatMap|isEmpty|isPresent|get)\( ]]; then
                            is_handled=true
                        fi
                    else
                        # Nullable: check for null check nearby (within 3 lines after)
                        local context
                        context=$(sed -n "${line_num},$((line_num + 3))p" "$caller_file")
                        if [[ "$context" =~ (!=\s*null|==\s*null|Objects\.requireNonNull|Optional\.ofNullable) ]]; then
                            is_handled=true
                        fi
                    fi

                    if [[ "$is_handled" != "true" ]]; then
                        candidates=$(echo "$candidates" | jq --arg sf "$source_file" \
                            --arg sm "$method_name" \
                            --arg sc "$method_class" \
                            --arg nt "$nullable_type" \
                            --arg tf "$caller_file" \
                            --argjson tl "$line_num" \
                            --arg tc "$line_content" \
                            '. + [{
                                issue_type: "NULL_PROPAGATION",
                                source_file: $sf,
                                source_method: $sm,
                                source_class: $sc,
                                nullable_type: $nt,
                                target_file: $tf,
                                target_line: $tl,
                                call_context: ($tc | gsub("^[[:space:]]+"; "") | .[0:100])
                            }]')
                    fi
                done <<< "$call_lines"
            fi
        done < "$files_list"
    done < <(echo "$nullable_methods" | jq -c '.[]')

    echo "$candidates" > "$output_file"
    log_debug "Null propagation candidates: $(echo "$candidates" | jq 'length')"
}

# Find potential resource ownership issues
# Look for resources created in one file, passed to another, with unclear cleanup
find_resource_ownership_candidates() {
    local files_list="$1"
    local method_sigs="$2"
    local output_file="$3"

    local candidates='[]'

    # Find methods that return resource types
    local resource_methods
    resource_methods=$(jq '[.[] | select(.returns_resource == true)]' "$method_sigs")

    # Also find resource creation patterns (new PrivateKey, new InputStream, etc.)
    local resource_patterns='(PrivateKey|InputStream|OutputStream|Reader|Writer|Connection|Channel|Socket|HttpClient)'

    while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        [[ ! -f "$file" ]] && continue

        # Find resource creation without try-with-resources
        local line_num=0
        local in_try_with=false
        while IFS= read -r line; do
            line_num=$((line_num + 1))

            # Track try-with-resources blocks
            if [[ "$line" =~ try[[:space:]]*\( ]]; then
                in_try_with=true
            fi
            if [[ "$in_try_with" == "true" ]] && [[ "$line" =~ \{[[:space:]]*$ ]]; then
                in_try_with=false
            fi

            # Find resource creation outside try-with-resources
            if [[ "$in_try_with" != "true" ]]; then
                if [[ "$line" =~ new[[:space:]]+$resource_patterns ]]; then
                    # Check if it's passed to another method (potential ownership transfer)
                    if [[ "$line" =~ \.[a-z]+\( ]] || [[ "$line" =~ return[[:space:]] ]]; then
                        candidates=$(echo "$candidates" | jq --arg f "$file" \
                            --argjson l "$line_num" \
                            --arg c "$line" \
                            '. + [{
                                issue_type: "RESOURCE_OWNERSHIP",
                                source_file: $f,
                                source_line: $l,
                                context: ($c | gsub("^[[:space:]]+"; "") | .[0:100]),
                                concern: "Resource created and passed without clear ownership"
                            }]')
                    fi
                fi
            fi
        done < "$file"
    done < "$files_list"

    echo "$candidates" > "$output_file"
    log_debug "Resource ownership candidates: $(echo "$candidates" | jq 'length')"
}

# Group related files for cross-file analysis
# Groups by: same package, inheritance relationship, or heavy import relationship
group_related_files() {
    local import_graph="$1"
    local output_file="$2"
    local max_group_size="${3:-5}"

    local groups='[]'

    # Group by package
    local packages
    packages=$(jq -r '[.[].package] | unique | .[]' "$import_graph")

    while IFS= read -r package; do
        [[ -z "$package" ]] && continue

        local pkg_files
        pkg_files=$(jq --arg p "$package" '[.[] | select(.package == $p) | .file]' "$import_graph")

        local file_count
        file_count=$(echo "$pkg_files" | jq 'length')

        if [[ "$file_count" -ge 2 ]] && [[ "$file_count" -le "$max_group_size" ]]; then
            groups=$(echo "$groups" | jq --arg p "$package" --argjson f "$pkg_files" \
                '. + [{group_type: "package", name: $p, files: $f}]')
        fi
    done <<< "$packages"

    # Group by inheritance (class + subclasses or interface + implementations)
    local classes_with_inheritance
    classes_with_inheritance=$(jq '[.[] | select(.extends != null or (.implements | length > 0))]' "$import_graph")

    while IFS= read -r class_json; do
        [[ -z "$class_json" ]] || [[ "$class_json" == "null" ]] && continue

        local class_name extends_class implements_list file
        class_name=$(echo "$class_json" | jq -r '.class')
        extends_class=$(echo "$class_json" | jq -r '.extends // empty')
        implements_list=$(echo "$class_json" | jq -r '.implements | .[]?' 2>/dev/null)
        file=$(echo "$class_json" | jq -r '.file')

        # Find the parent class/interface file
        if [[ -n "$extends_class" ]]; then
            local parent_file
            parent_file=$(jq -r --arg c "$extends_class" '.[] | select(.class == $c) | .file' "$import_graph" | head -1)

            if [[ -n "$parent_file" ]] && [[ "$parent_file" != "null" ]]; then
                groups=$(echo "$groups" | jq --arg n "$extends_class" --arg p "$parent_file" --arg c "$file" \
                    '. + [{group_type: "inheritance", name: ("extends_" + $n), files: [$p, $c]}]')
            fi
        fi
    done < <(echo "$classes_with_inheritance" | jq -c '.[]')

    # Deduplicate groups (same files in different groups)
    groups=$(echo "$groups" | jq 'unique_by(.files | sort)')

    echo "$groups" > "$output_file"
    log_debug "File groups created: $(echo "$groups" | jq 'length')"
}

#######################################
# Cross-File Analysis Prompts
#######################################

build_null_propagation_prompt() {
    local candidates_json="$1"
    local files_content="$2"

    cat << 'EOF'
You are analyzing Java code for null propagation issues across multiple files.

## What to Look For
Null propagation occurs when:
1. A method returns null or Optional.empty() in some cases
2. Callers of that method don't check for null/empty before using the result
3. This can cause NullPointerException at runtime

## Severity Guidelines
- T1 (Critical): Null dereference in production code path, high likelihood of NPE
- T2 (High): Null dereference possible but requires specific conditions
- T3 (Medium): Null handling is inconsistent but unlikely to cause issues
- T4 (Low): Style issue, could use Optional pattern better

## Candidates to Verify
These are static analysis candidates that MAY have null propagation issues.
Analyze each one and determine if it's a real issue or false positive.

EOF
    echo "$candidates_json" | jq -r '.[] | "### Candidate: \(.source_class).\(.source_method) -> \(.target_file):\(.target_line)\n- Nullable type: \(.nullable_type)\n- Call context: `\(.call_context)`\n"'

    cat << 'EOF'

## Source Files
EOF
    echo "$files_content"

    cat << 'EOF'

## Output Rules
- For each candidate, determine: CONFIRMED (real issue) or FALSE_POSITIVE (handled correctly)
- If CONFIRMED, include the propagation chain and fix suggestion
- Focus only on cross-file issues (caller in different file than method)
- Do NOT flag Optional usage that properly handles empty case
EOF
}

build_resource_ownership_prompt() {
    local candidates_json="$1"
    local files_content="$2"

    cat << 'EOF'
You are analyzing Java code for resource ownership issues across multiple files.

## What to Look For
Resource ownership issues occur when:
1. A resource (Closeable/AutoCloseable) is created in one file
2. It's passed to another file/method
3. The cleanup responsibility is unclear or missing

## Brane SDK Specific Rules
EOF
    echo "$BRANE_CROSS_FILE_PATTERNS" | grep -A 10 "Resource Ownership"
    cat << 'EOF'

## Candidates to Verify
EOF
    echo "$candidates_json" | jq -r '.[] | "### Candidate: \(.source_file):\(.source_line)\n- Context: `\(.context)`\n- Concern: \(.concern)\n"'

    cat << 'EOF'

## Source Files
EOF
    echo "$files_content"

    cat << 'EOF'

## Output Rules
- For each candidate, determine: CONFIRMED or FALSE_POSITIVE
- T1 for PrivateKey not destroyed, T2 for other resources
- Suggest explicit ownership documentation or try-with-resources
EOF
}

build_contract_violation_prompt() {
    local group_json="$1"
    local files_content="$2"

    cat << 'EOF'
You are analyzing Java code for contract violations between interfaces and implementations.

## What to Look For
Contract violations occur when:
1. An interface or abstract class defines a contract (via Javadoc, return type, exceptions)
2. An implementation violates that contract

## Common Contract Violations
- Method documented as "never returns null" but implementation can return null
- Method not declared to throw exception but implementation throws unchecked exceptions
- Builder.build() doesn't validate required fields
- equals/hashCode contract violations
- Comparable contract violations

## Files in This Group
EOF
    echo "$group_json" | jq -r '"Group: \(.name) (\(.group_type))\nFiles: \(.files | join(", "))"'

    cat << 'EOF'

## Source Files
EOF
    echo "$files_content"

    cat << 'EOF'

## Output Rules
- Only flag cross-file violations (interface in one file, implementation in another)
- Include the contract (documented or implied) being violated
- T2 for null contract violations, T3 for other contract issues
EOF
}

#######################################
# Run Cross-File Analysis
#######################################

run_cross_file_analysis() {
    local analysis_type="$1"  # NULL_PROPAGATION, RESOURCE_OWNERSHIP, CONTRACT_VIOLATION
    local candidates_or_group="$2"  # JSON file with candidates or group info
    local files_list="$3"
    local output_file="$4"
    local model="${5:-sonnet}"

    local files_content=""
    local prompt=""

    # Build file content based on analysis type
    case "$analysis_type" in
        NULL_PROPAGATION)
            # Get unique files from candidates
            local relevant_files
            relevant_files=$(jq -r '([.[].source_file, .[].target_file] | unique | .[])' "$candidates_or_group")

            while IFS= read -r file; do
                [[ -z "$file" ]] && continue
                [[ ! -f "$file" ]] && continue
                local content
                content=$(format_file_with_line_numbers "$file" 300)
                files_content+="
### $file
\`\`\`java
$content
\`\`\`
"
            done <<< "$relevant_files"

            local candidates
            candidates=$(cat "$candidates_or_group")
            prompt=$(build_null_propagation_prompt "$candidates" "$files_content")
            ;;

        RESOURCE_OWNERSHIP)
            local relevant_files
            relevant_files=$(jq -r '([.[].source_file] | unique | .[])' "$candidates_or_group")

            while IFS= read -r file; do
                [[ -z "$file" ]] && continue
                [[ ! -f "$file" ]] && continue
                local content
                content=$(format_file_with_line_numbers "$file" 300)
                files_content+="
### $file
\`\`\`java
$content
\`\`\`
"
            done <<< "$relevant_files"

            local candidates
            candidates=$(cat "$candidates_or_group")
            prompt=$(build_resource_ownership_prompt "$candidates" "$files_content")
            ;;

        CONTRACT_VIOLATION)
            local group_files
            group_files=$(jq -r '.files[]' "$candidates_or_group")

            while IFS= read -r file; do
                [[ -z "$file" ]] && continue
                [[ ! -f "$file" ]] && continue
                local content
                content=$(format_file_with_line_numbers "$file" 400)
                files_content+="
### $file
\`\`\`java
$content
\`\`\`
"
            done <<< "$group_files"

            local group
            group=$(cat "$candidates_or_group")
            prompt=$(build_contract_violation_prompt "$group" "$files_content")
            ;;
    esac

    local response
    if response=$(claude_call "$prompt" "$CROSS_FILE_SCHEMA" "$model"); then
        echo "$response" | jq -r '.structured_output // empty' > "$output_file"
        log_debug "Cross-file analysis ($analysis_type) complete"
        return 0
    else
        log_warn "Cross-file analysis ($analysis_type) failed"
        echo '{"findings":[]}' > "$output_file"
        return 1
    fi
}

#######################################
# Aggregate Cross-File Findings
#######################################

aggregate_cross_file_findings() {
    local work_dir="$1"
    local output_file="$2"

    local all_findings='[]'

    # Aggregate from each analysis type
    for analysis_type in null_propagation resource_ownership contract_violation; do
        local result_file="$work_dir/${analysis_type}_result.json"
        [[ ! -f "$result_file" ]] && continue

        local findings
        findings=$(jq -r '.findings // []' "$result_file" 2>/dev/null || echo '[]')

        # Add analysis type tag
        findings=$(echo "$findings" | jq --arg t "$analysis_type" \
            '[.[] | . + {cross_file_type: $t}]')

        all_findings=$(echo "$all_findings" "$findings" | jq -s 'add')
    done

    # Sort by severity
    all_findings=$(echo "$all_findings" | jq '
        sort_by(
            if .severity == "T1" then 0
            elif .severity == "T2" then 1
            elif .severity == "T3" then 2
            else 3 end
        )
    ')

    echo "$all_findings" > "$output_file"

    local count
    count=$(echo "$all_findings" | jq 'length')
    log_info "Cross-file analysis findings: $count total"
}

#######################################
# Main Entry Point
#######################################

# Full cross-file analysis pipeline
# Called from orchestrator's phase_cross_file_analysis
run_full_cross_file_analysis() {
    local files_list="$1"
    local work_dir="$2"
    local model="${3:-sonnet}"

    log_info "Starting cross-file analysis..."

    local cross_work_dir="$work_dir/cross_file"
    mkdir -p "$cross_work_dir"

    # Step 1: Extract static relationships
    log_info "  Extracting import graph..."
    extract_import_graph "$files_list" "$cross_work_dir/import_graph.json"

    log_info "  Extracting method signatures..."
    extract_method_signatures "$files_list" "$cross_work_dir/method_sigs.json"

    # Step 2: Find candidates for cross-file issues
    log_info "  Finding null propagation candidates..."
    find_null_propagation_candidates \
        "$cross_work_dir/import_graph.json" \
        "$cross_work_dir/method_sigs.json" \
        "$files_list" \
        "$cross_work_dir/null_candidates.json"

    log_info "  Finding resource ownership candidates..."
    find_resource_ownership_candidates \
        "$files_list" \
        "$cross_work_dir/method_sigs.json" \
        "$cross_work_dir/resource_candidates.json"

    log_info "  Grouping related files..."
    group_related_files \
        "$cross_work_dir/import_graph.json" \
        "$cross_work_dir/file_groups.json" \
        5

    # Step 3: Run LLM analysis on candidates (in parallel)
    local null_count resource_count group_count
    null_count=$(jq 'length' "$cross_work_dir/null_candidates.json" 2>/dev/null || echo "0")
    resource_count=$(jq 'length' "$cross_work_dir/resource_candidates.json" 2>/dev/null || echo "0")
    group_count=$(jq 'length' "$cross_work_dir/file_groups.json" 2>/dev/null || echo "0")

    PIDS=()

    # Run null propagation analysis if candidates exist
    if [[ "$null_count" -gt 0 ]]; then
        log_info "  Analyzing $null_count null propagation candidates..."
        (
            run_cross_file_analysis "NULL_PROPAGATION" \
                "$cross_work_dir/null_candidates.json" \
                "$files_list" \
                "$cross_work_dir/null_propagation_result.json" \
                "$model"
        ) &
        PIDS+=($!)
    fi

    # Run resource ownership analysis if candidates exist
    if [[ "$resource_count" -gt 0 ]]; then
        log_info "  Analyzing $resource_count resource ownership candidates..."
        (
            run_cross_file_analysis "RESOURCE_OWNERSHIP" \
                "$cross_work_dir/resource_candidates.json" \
                "$files_list" \
                "$cross_work_dir/resource_ownership_result.json" \
                "$model"
        ) &
        PIDS+=($!)
    fi

    # Run contract violation analysis on first few groups
    local groups_to_analyze=3
    local analyzed=0
    if [[ "$group_count" -gt 0 ]]; then
        while IFS= read -r group_json; do
            [[ "$analyzed" -ge "$groups_to_analyze" ]] && break
            [[ -z "$group_json" ]] || [[ "$group_json" == "null" ]] && continue

            local group_name
            group_name=$(echo "$group_json" | jq -r '.name')
            log_info "  Analyzing contract violations in group: $group_name"

            echo "$group_json" > "$cross_work_dir/group_${analyzed}.json"
            (
                run_cross_file_analysis "CONTRACT_VIOLATION" \
                    "$cross_work_dir/group_${analyzed}.json" \
                    "$files_list" \
                    "$cross_work_dir/contract_violation_${analyzed}_result.json" \
                    "$model"
            ) &
            PIDS+=($!)
            analyzed=$((analyzed + 1))
        done < <(jq -c '.[]' "$cross_work_dir/file_groups.json")
    fi

    # Wait for all analysis to complete
    log_info "  Waiting for cross-file analysis to complete..."
    local pid
    for pid in "${PIDS[@]}"; do
        wait "$pid" 2>/dev/null || true
    done

    # Merge contract violation results
    if [[ "$analyzed" -gt 0 ]]; then
        local merged_contracts='{"findings":[]}'
        local i=0
        while [[ $i -lt $analyzed ]]; do
            local result_file="$cross_work_dir/contract_violation_${i}_result.json"
            if [[ -f "$result_file" ]]; then
                local findings
                findings=$(jq '.findings // []' "$result_file")
                merged_contracts=$(echo "$merged_contracts" | jq --argjson f "$findings" '.findings += $f')
            fi
            i=$((i + 1))
        done
        echo "$merged_contracts" > "$cross_work_dir/contract_violation_result.json"
    fi

    # Step 4: Aggregate all findings
    aggregate_cross_file_findings "$cross_work_dir" "$cross_work_dir/all_cross_file.json"

    echo "$cross_work_dir/all_cross_file.json"
}
