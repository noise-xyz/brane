#!/bin/bash
# Specialized Discovery Library for Code Review Orchestrator v7
#
# Provides category-specific deep analysis beyond pattern matching:
#   1. Architectural Analysis - module boundaries, dependencies, API surface
#   2. Code Quality Analysis - complexity, duplication, idiom usage
#   3. Safety Analysis - crypto handling, input validation, data flow
#   4. Performance Analysis - allocations, blocking, hot paths
#
# Usage: source this file from orchestrator

#######################################
# Category Schemas
#######################################

readonly ARCH_DISCOVERY_SCHEMA='{
  "type": "object",
  "properties": {
    "findings": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "file": {"type": "string"},
          "line": {"type": "integer"},
          "issue_type": {"type": "string", "enum": ["LAYER_VIOLATION", "CIRCULAR_DEP", "INTERNAL_LEAK", "API_INCONSISTENCY", "MODULE_COUPLING", "ABSTRACTION_LEAK"]},
          "severity": {"type": "string", "enum": ["T1", "T2", "T3", "T4"]},
          "description": {"type": "string"},
          "involved_modules": {"type": "array", "items": {"type": "string"}},
          "fix_suggestion": {"type": "string"}
        },
        "required": ["file", "line", "issue_type", "severity", "description"]
      }
    },
    "module_health": {
      "type": "object",
      "properties": {
        "clean_boundaries": {"type": "boolean"},
        "concerns": {"type": "array", "items": {"type": "string"}}
      }
    }
  },
  "required": ["findings"]
}'

readonly QUALITY_DISCOVERY_SCHEMA='{
  "type": "object",
  "properties": {
    "findings": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "file": {"type": "string"},
          "line": {"type": "integer"},
          "issue_type": {"type": "string", "enum": ["HIGH_COMPLEXITY", "DUPLICATION", "MISSING_JAVA21_IDIOM", "POOR_NAMING", "GOD_CLASS", "LONG_METHOD", "FEATURE_ENVY", "DATA_CLUMP"]},
          "severity": {"type": "string", "enum": ["T2", "T3", "T4"]},
          "description": {"type": "string"},
          "metric": {"type": "string", "description": "e.g., cyclomatic complexity = 15"},
          "refactoring": {"type": "string"}
        },
        "required": ["file", "line", "issue_type", "severity", "description"]
      }
    }
  },
  "required": ["findings"]
}'

readonly SAFETY_DISCOVERY_SCHEMA='{
  "type": "object",
  "properties": {
    "findings": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "file": {"type": "string"},
          "line": {"type": "integer"},
          "issue_type": {"type": "string", "enum": ["SECRET_EXPOSURE", "MISSING_VALIDATION", "TIMING_ATTACK", "INTEGER_OVERFLOW", "INJECTION", "SENSITIVE_DATA_LEAK", "MISSING_CLEANUP", "UNSAFE_DESERIALIZATION"]},
          "severity": {"type": "string", "enum": ["T1", "T2", "T3"]},
          "description": {"type": "string"},
          "attack_vector": {"type": "string"},
          "cwe": {"type": "string", "description": "CWE ID if applicable"},
          "fix": {"type": "string"}
        },
        "required": ["file", "line", "issue_type", "severity", "description"]
      }
    },
    "security_posture": {
      "type": "object",
      "properties": {
        "input_validation_present": {"type": "boolean"},
        "sensitive_data_handling": {"type": "string", "enum": ["GOOD", "ADEQUATE", "NEEDS_REVIEW", "POOR"]}
      }
    }
  },
  "required": ["findings"]
}'

readonly PERF_DISCOVERY_SCHEMA='{
  "type": "object",
  "properties": {
    "findings": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "file": {"type": "string"},
          "line": {"type": "integer"},
          "issue_type": {"type": "string", "enum": ["ALLOCATION_IN_LOOP", "BLOCKING_IN_HOT_PATH", "MISSING_CACHE", "REGEX_COMPILE_LOOP", "STRING_CONCAT_LOOP", "N_PLUS_ONE", "UNBOUNDED_COLLECTION", "SYNC_CONTENTION"]},
          "severity": {"type": "string", "enum": ["T2", "T3", "T4"]},
          "description": {"type": "string"},
          "estimated_impact": {"type": "string", "enum": ["HIGH", "MEDIUM", "LOW"]},
          "optimization": {"type": "string"}
        },
        "required": ["file", "line", "issue_type", "severity", "description"]
      }
    },
    "hot_paths_identified": {
      "type": "array",
      "items": {"type": "string"}
    }
  },
  "required": ["findings"]
}'

#######################################
# Brane-Specific Domain Knowledge
#######################################

readonly BRANE_MODULE_BOUNDARIES='
Module Dependency Rules (MUST NOT violate):
- brane-primitives: ZERO external dependencies, only java.* imports
- brane-core: May depend on brane-primitives, BouncyCastle, Jackson
- brane-kzg: May depend on brane-core, c-kzg native binding
- brane-rpc: May depend on brane-core, Netty, Disruptor
- brane-contract: May depend on brane-rpc, brane-core

FORBIDDEN patterns:
- ANY import of sh.brane.internal.* from outside its own module
- brane-primitives importing anything from brane-core
- brane-kzg importing from brane-rpc
- Circular dependencies between any modules
- web3j types (sh.brane.internal.web3j.*) in ANY public API
'

readonly BRANE_SENSITIVE_TYPES='
Sensitive Types (require special handling):
- PrivateKey: MUST call destroy() when done, NEVER log, NEVER include in toString()
- MnemonicWallet: Keep instances short-lived, phrase in memory = access to all keys
- Keccak256: MUST call cleanup() in pooled/web threads to prevent ThreadLocal leaks

Sensitive Operations:
- Signature creation/verification: timing-safe comparison required
- Wei arithmetic: overflow checks required for untrusted inputs
- RLP decoding: length validation required before allocation
'

readonly BRANE_HOT_PATHS='
Known Hot Paths (performance critical):
- RPC request handlers (BraneProvider.*)
- ABI encoding/decoding (Abi.encode*, Abi.decode*)
- Transaction signing (Signer.sign*)
- Keccak256.hash() - called frequently
- Rlp.encode/decode - called per transaction field

Performance Expectations:
- RPC handlers: <1ms for simple calls
- ABI encoding: <100us for typical structs
- Signing: <10ms (crypto bound)
'

#######################################
# Architectural Analysis
#######################################

build_arch_analysis_prompt() {
    local files_content="$1"
    local import_summary="$2"

    cat << 'EOF'
You are an Architecture Reviewer analyzing a Java SDK for Ethereum/EVM operations.

## Module Structure
EOF
    echo "$BRANE_MODULE_BOUNDARIES"
    cat << 'EOF'

## Import Analysis (extracted from codebase)
EOF
    echo "$import_summary"
    cat << 'EOF'

## Files Under Review
EOF
    echo "$files_content"
    cat << 'EOF'

## Your Task
Identify architectural violations and concerns:

### MUST FLAG (T1/T2):
1. **INTERNAL_LEAK**: Any public class/method exposing sh.brane.internal.* types
   - Check return types, parameter types, thrown exceptions
   - web3j types MUST NOT appear in public API

2. **LAYER_VIOLATION**: Module importing from forbidden dependency
   - brane-primitives must have zero brane-* imports
   - brane-kzg must not import brane-rpc

3. **CIRCULAR_DEP**: Package A imports B, B imports A (directly or transitively)

### SHOULD FLAG (T3/T4):
4. **API_INCONSISTENCY**: Similar operations with different patterns
   - e.g., some methods return Optional, similar ones return null
   - Inconsistent naming (getX vs fetchX vs loadX)

5. **MODULE_COUPLING**: Too many cross-module dependencies in one class
   - Class imports from 3+ different brane modules = potential god class

6. **ABSTRACTION_LEAK**: Implementation details in public interface
   - Internal data structures exposed
   - Implementation-specific exceptions in public API

## Output Rules
- For each finding, specify the exact file and line
- Include which modules are involved
- Suggest specific fix (move class, add wrapper, etc.)
- Do NOT flag test code or examples
EOF
}

# Extract import summary for architectural analysis
extract_import_summary() {
    local files_list="$1"
    local output=""

    while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        [[ ! -f "$file" ]] && continue

        local imports
        imports=$(grep "^import " "$file" 2>/dev/null | sort -u)
        if [[ -n "$imports" ]]; then
            local module
            module=$(echo "$file" | grep -oE 'brane-[a-z]+' | head -1)
            output+="### $file (module: ${module:-unknown})\n"
            output+="$imports\n\n"
        fi
    done < "$files_list"

    printf '%b' "$output"
}

# Detect cross-module imports
analyze_module_dependencies() {
    local files_list="$1"
    local violations=""

    while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        [[ ! -f "$file" ]] && continue

        local source_module
        source_module=$(echo "$file" | grep -oE 'brane-[a-z]+' | head -1)

        # Check for internal package leaks in public API
        if [[ "$file" != *"/internal/"* ]] && [[ "$file" != *"Test.java" ]]; then
            if grep -q "io\.brane\.internal\." "$file" 2>/dev/null; then
                local line_num
                line_num=$(grep -n "io\.brane\.internal\." "$file" | head -1 | cut -d: -f1)
                violations+="INTERNAL_LEAK|$file|$line_num|internal package used in public code\n"
            fi
        fi

        # Check primitives module has no brane imports
        if [[ "$source_module" == "brane-primitives" ]]; then
            if grep -qE "^import io\.brane\.(core|rpc|kzg|contract)" "$file" 2>/dev/null; then
                local line_num
                line_num=$(grep -nE "^import io\.brane\.(core|rpc|kzg|contract)" "$file" | head -1 | cut -d: -f1)
                violations+="LAYER_VIOLATION|$file|$line_num|primitives must not import other brane modules\n"
            fi
        fi

        # Check kzg doesn't import rpc
        if [[ "$source_module" == "brane-kzg" ]]; then
            if grep -q "^import io\.brane\.rpc" "$file" 2>/dev/null; then
                local line_num
                line_num=$(grep -n "^import io\.brane\.rpc" "$file" | head -1 | cut -d: -f1)
                violations+="LAYER_VIOLATION|$file|$line_num|kzg must not import rpc\n"
            fi
        fi
    done < "$files_list"

    printf '%b' "$violations"
}

#######################################
# Code Quality Analysis
#######################################

build_quality_analysis_prompt() {
    local files_content="$1"

    cat << 'EOF'
You are a Code Quality Reviewer for a modern Java 21 SDK.

## Java 21 Idioms to Check
The codebase should use modern Java features:
- Records for immutable value types (not classes with final fields + constructor + getters)
- Pattern matching: `if (x instanceof Foo f)` not `if (x instanceof Foo) { Foo f = (Foo) x; }`
- Switch expressions: `return switch(x) { ... }` not if-else chains
- Sealed classes for type hierarchies
- `var` for local variables when type is obvious from RHS
- `List.of()`, `Map.of()` for immutable collections (not Collections.unmodifiable*)
- `Optional` for nullable returns (not null + @Nullable)
- Text blocks for multi-line strings

## Files Under Review
EOF
    echo "$files_content"
    cat << 'EOF'

## Your Task
Identify code quality issues that impact maintainability:

### SHOULD FLAG (T2/T3):
1. **HIGH_COMPLEXITY**: Method with cyclomatic complexity > 10
   - Count: if, else, for, while, case, catch, &&, ||, ?:
   - Methods should do one thing

2. **LONG_METHOD**: Method > 40 lines (excluding comments/blanks)
   - Extract helper methods

3. **GOD_CLASS**: Class with > 10 public methods or > 500 lines
   - Split responsibilities

4. **MISSING_JAVA21_IDIOM**: Not using modern Java where appropriate
   - Class that should be a record
   - instanceof without pattern matching
   - if-else chain that should be switch expression (3+ branches)

5. **FEATURE_ENVY**: Method that uses another class's data more than its own
   - Move method to the class it envies

### MAY FLAG (T4):
6. **DUPLICATION**: Similar code blocks (>10 lines) in multiple places
   - Extract to shared method

7. **POOR_NAMING**: Unclear names that don't express intent
   - e.g., `process()`, `handle()`, `doIt()`, single-letter variables (except loop indices)

8. **DATA_CLUMP**: Same group of parameters passed together repeatedly
   - Extract parameter object

## Output Rules
- Include specific line numbers
- For complexity issues, note the count/metric
- Suggest specific refactoring
- Do NOT flag test code
- Do NOT flag code that's intentionally simple (data classes, etc.)
EOF
}

#######################################
# Safety/Security Analysis
#######################################

build_safety_analysis_prompt() {
    local files_content="$1"

    cat << 'EOF'
You are a Security Reviewer for a cryptocurrency/blockchain SDK. Security is CRITICAL.

## Sensitive Types in This Codebase
EOF
    echo "$BRANE_SENSITIVE_TYPES"
    cat << 'EOF'

## Files Under Review
EOF
    echo "$files_content"
    cat << 'EOF'

## Your Task
Identify security vulnerabilities and unsafe patterns:

### MUST FLAG (T1 - Critical):
1. **SECRET_EXPOSURE**: Private keys, mnemonics, or seeds in logs/toString/exceptions
   - PrivateKey.toString() must NOT reveal key material
   - Log statements must NOT include sensitive data
   - Exception messages must NOT include keys

2. **TIMING_ATTACK**: Non-constant-time comparison of secrets
   - Signature verification must use constant-time equals
   - MAC verification must use constant-time equals
   - DO NOT use `.equals()` or `Arrays.equals()` for secrets

3. **MISSING_CLEANUP**: PrivateKey not destroyed after use
   - PrivateKey.destroy() must be called
   - Use try-finally or try-with-resources pattern

### MUST FLAG (T2 - High):
4. **INTEGER_OVERFLOW**: Arithmetic on untrusted input without bounds check
   - Wei amounts from RPC responses
   - Gas calculations
   - Array indices from external data

5. **MISSING_VALIDATION**: External input used without validation
   - RPC responses parsed directly
   - User-provided addresses/hashes not validated
   - Lengths not checked before allocation

6. **INJECTION**: String concatenation into queries/commands
   - SQL (unlikely in this codebase but check)
   - Shell commands
   - Dynamic class loading

### SHOULD FLAG (T3):
7. **SENSITIVE_DATA_LEAK**: Sensitive data copied unnecessarily
   - byte[] containing keys should be zeroed after use
   - Defensive copies of sensitive arrays

8. **UNSAFE_DESERIALIZATION**: Deserializing untrusted data
   - JSON parsing without schema validation
   - RLP decoding without length limits

## Domain-Specific Checks
For this Ethereum SDK specifically:
- Keccak256.cleanup() must be called in pooled thread contexts
- Wei arithmetic must handle BigInteger overflow for untrusted values
- Address.from() should validate checksum when provided
- Signature (r, s, v) values must be validated (s not malleable)

## Output Rules
- T1 findings are CRITICAL - must be 100% confident
- Include CWE IDs where applicable
- Describe the attack vector
- Provide specific fix
EOF
}

#######################################
# Performance Analysis
#######################################

build_perf_analysis_prompt() {
    local files_content="$1"

    cat << 'EOF'
You are a Performance Reviewer for a high-throughput blockchain SDK.

## Performance Context
EOF
    echo "$BRANE_HOT_PATHS"
    cat << 'EOF'

## Files Under Review
EOF
    echo "$files_content"
    cat << 'EOF'

## Your Task
Identify performance issues that impact throughput or latency:

### SHOULD FLAG (T2 - causes visible latency):
1. **BLOCKING_IN_HOT_PATH**: Synchronous I/O or locks in frequently-called code
   - synchronized blocks in RPC handlers
   - Blocking network calls in request processing
   - File I/O in encoding/decoding paths

2. **N_PLUS_ONE**: Loop that makes repeated calls that could be batched
   - Multiple RPC calls that could be single batch
   - Repeated map lookups that could be single lookup

3. **UNBOUNDED_COLLECTION**: Collection that grows without limit
   - Caches without eviction
   - Lists that accumulate indefinitely
   - Memory leaks from retained references

### SHOULD FLAG (T3 - impacts throughput):
4. **ALLOCATION_IN_LOOP**: Object creation inside hot loops
   - new ArrayList/HashMap in loop body
   - String concatenation in loop (creates new String each iteration)
   - Autoboxing in loop (int -> Integer)

5. **REGEX_COMPILE_LOOP**: Pattern.compile() inside loop or hot method
   - Compile once, reuse Pattern

6. **STRING_CONCAT_LOOP**: String + String in loop
   - Use StringBuilder

7. **SYNC_CONTENTION**: Lock that's too coarse-grained
   - synchronized on whole method when only part needs sync
   - Single lock for unrelated operations

### MAY FLAG (T4 - micro-optimization):
8. **MISSING_CACHE**: Repeated expensive computation with same inputs
   - Keccak256 hash computed multiple times for same data
   - Repeated parsing of same string

## What NOT to Flag
- Virtual thread + blocking I/O is FINE (virtual threads are designed for this)
- Allocations outside hot paths are FINE
- Readability > micro-optimization for non-hot code

## Output Rules
- Focus on code in hot paths (RPC handlers, ABI, signing, hashing)
- Estimate impact: HIGH (visible latency), MEDIUM (throughput), LOW (micro)
- Suggest specific optimization
- Include line numbers
EOF
}

#######################################
# Run Specialized Discovery Pass
#######################################

run_specialized_discovery() {
    local category="$1"        # ARCH, QUALITY, SAFETY, PERF
    local files_list="$2"
    local output_file="$3"
    local model="${4:-sonnet}" # Specialized analysis needs reasoning

    local schema prompt files_content

    # Build file content with line numbers
    files_content=""
    local idx=0
    while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        [[ ! -f "$file" ]] && continue
        idx=$((idx + 1))

        local content
        content=$(format_file_with_line_numbers "$file" 500)
        files_content+="
### [$idx] $file
\`\`\`java
$content
\`\`\`
"
    done < "$files_list"

    case "$category" in
        ARCH)
            schema="$ARCH_DISCOVERY_SCHEMA"
            local import_summary
            import_summary=$(extract_import_summary "$files_list")
            prompt=$(build_arch_analysis_prompt "$files_content" "$import_summary")
            ;;
        QUALITY)
            schema="$QUALITY_DISCOVERY_SCHEMA"
            prompt=$(build_quality_analysis_prompt "$files_content")
            ;;
        SAFETY)
            schema="$SAFETY_DISCOVERY_SCHEMA"
            prompt=$(build_safety_analysis_prompt "$files_content")
            ;;
        PERF)
            schema="$PERF_DISCOVERY_SCHEMA"
            prompt=$(build_perf_analysis_prompt "$files_content")
            ;;
        *)
            log_error "Unknown specialized category: $category"
            return 1
            ;;
    esac

    local response
    if response=$(claude_call "$prompt" "$schema" "$model"); then
        echo "$response" | jq -r '.structured_output // empty' > "$output_file"
        log_debug "Specialized discovery ($category) complete"
        return 0
    else
        log_warn "Specialized discovery ($category) failed"
        echo '{"findings":[]}' > "$output_file"
        return 1
    fi
}

#######################################
# Pre-analysis: Static Checks (no LLM)
#######################################

# Run fast static checks before LLM analysis
run_static_prechecks() {
    local files_list="$1"
    local output_file="$2"

    local findings='[]'

    while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        [[ ! -f "$file" ]] && continue

        # Check 1: internal package leak
        if [[ "$file" != *"/internal/"* ]] && [[ "$file" != *"Test.java" ]]; then
            local leak_lines
            leak_lines=$(grep -n "io\.brane\.internal\." "$file" 2>/dev/null || true)
            if [[ -n "$leak_lines" ]]; then
                while IFS=: read -r line_num content; do
                    findings=$(echo "$findings" | jq --arg f "$file" --argjson l "$line_num" \
                        --arg d "Internal package sh.brane.internal.* exposed in public API" \
                        '. + [{file: $f, line: $l, category: "ARCH", issue_type: "INTERNAL_LEAK", severity: "T1", description: $d, source: "static"}]')
                done <<< "$leak_lines"
            fi
        fi

        # Check 2: PrivateKey without destroy
        if grep -q "PrivateKey" "$file" 2>/dev/null; then
            if ! grep -q "\.destroy()" "$file" 2>/dev/null; then
                local pk_line
                pk_line=$(grep -n "PrivateKey" "$file" | head -1 | cut -d: -f1)
                if [[ -n "$pk_line" ]]; then
                    findings=$(echo "$findings" | jq --arg f "$file" --argjson l "$pk_line" \
                        --arg d "PrivateKey used but destroy() not found in file" \
                        '. + [{file: $f, line: $l, category: "SAFETY", issue_type: "MISSING_CLEANUP", severity: "T2", description: $d, source: "static"}]')
                fi
            fi
        fi

        # Check 3: Keccak256 in non-static context without cleanup
        if grep -q "Keccak256\." "$file" 2>/dev/null; then
            if ! grep -q "Keccak256\.cleanup()" "$file" 2>/dev/null; then
                # Only flag if it looks like a request handler or thread pool context
                if grep -qE "(Handler|Service|Provider|Executor)" "$file" 2>/dev/null; then
                    local keccak_line
                    keccak_line=$(grep -n "Keccak256\." "$file" | head -1 | cut -d: -f1)
                    if [[ -n "$keccak_line" ]]; then
                        findings=$(echo "$findings" | jq --arg f "$file" --argjson l "$keccak_line" \
                            --arg d "Keccak256 used in handler/service context without cleanup() - potential ThreadLocal leak" \
                            '. + [{file: $f, line: $l, category: "SAFETY", issue_type: "MISSING_CLEANUP", severity: "T3", description: $d, source: "static"}]')
                    fi
                fi
            fi
        fi

        # Check 4: String concat in loop (simple heuristic)
        local in_loop=false
        local loop_start_line=0
        local line_num=0
        while IFS= read -r line; do
            line_num=$((line_num + 1))
            if [[ "$line" =~ (for|while)[[:space:]]*\( ]]; then
                in_loop=true
                loop_start_line=$line_num
            fi
            if [[ "$in_loop" == "true" ]] && [[ "$line" =~ \+= ]]; then
                # Check if it's string concat
                if [[ "$line" =~ \".*\" ]] || [[ "$line" =~ String ]]; then
                    findings=$(echo "$findings" | jq --arg f "$file" --argjson l "$line_num" \
                        --arg d "Possible string concatenation in loop - consider StringBuilder" \
                        '. + [{file: $f, line: $l, category: "PERF", issue_type: "STRING_CONCAT_LOOP", severity: "T4", description: $d, source: "static"}]')
                fi
            fi
            if [[ "$line" =~ ^\s*\} ]] && [[ "$in_loop" == "true" ]]; then
                in_loop=false
            fi
        done < "$file"

        # Check 5: Pattern.compile in method (not static field)
        local pattern_lines
        pattern_lines=$(grep -n "Pattern\.compile" "$file" 2>/dev/null || true)
        if [[ -n "$pattern_lines" ]]; then
            while IFS=: read -r line_num content; do
                # Check if it's in a method (indented) vs static field (less indented)
                local indent
                indent=$(echo "$content" | sed 's/[^ ].*//' | wc -c)
                if [[ "$indent" -gt 8 ]]; then  # Likely in a method
                    findings=$(echo "$findings" | jq --arg f "$file" --argjson l "$line_num" \
                        --arg d "Pattern.compile() in method body - compile once in static field" \
                        '. + [{file: $f, line: $l, category: "PERF", issue_type: "REGEX_COMPILE_LOOP", severity: "T3", description: $d, source: "static"}]')
                fi
            done <<< "$pattern_lines"
        fi

    done < "$files_list"

    echo "$findings" > "$output_file"
    local count
    count=$(echo "$findings" | jq 'length')
    log_info "Static prechecks: $count findings"
}

#######################################
# Aggregate Specialized Findings
#######################################

aggregate_specialized_findings() {
    local work_dir="$1"
    local output_file="$2"

    local all_findings='[]'

    # Aggregate from each category
    for category in ARCH QUALITY SAFETY PERF STATIC; do
        local result_file="$work_dir/${category,,}_result.json"
        [[ ! -f "$result_file" ]] && continue

        local findings
        findings=$(jq -r '.findings // []' "$result_file" 2>/dev/null || echo '[]')

        # Add category tag if not present
        findings=$(echo "$findings" | jq --arg cat "$category" \
            '[.[] | . + {analysis_type: $cat}]')

        all_findings=$(echo "$all_findings" "$findings" | jq -s 'add')
    done

    # Deduplicate (same file+line within 3 lines)
    all_findings=$(echo "$all_findings" | jq '
        group_by(.file) |
        map(
            sort_by(.line) |
            reduce .[] as $item (
                [];
                if length == 0 then
                    [$item]
                else
                    ((.[-1].line - $item.line) | if . < 0 then -. else . end) as $diff |
                    if $diff <= 3 and .[-1].issue_type == $item.issue_type then
                        .
                    else
                        . + [$item]
                    end
                end
            )
        ) |
        flatten |
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
    log_info "Aggregated specialized findings: $count total"
}
