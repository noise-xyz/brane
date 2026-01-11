#!/usr/bin/env bash
# Unit tests for hallucination_defense.sh
#
# Run: bash .claude/scripts/lib/test_hallucination_defense.sh
#
# Exit codes:
#   0 = all tests passed
#   1 = one or more tests failed

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_TEMP_DIR=""
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Color output (disabled if not a terminal)
if [[ -t 1 ]]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[0;33m'
    NC='\033[0m'
else
    RED=''
    GREEN=''
    YELLOW=''
    NC=''
fi

#######################################
# Test Framework
#######################################

setup() {
    TEST_TEMP_DIR=$(mktemp -d)

    # Source the library under test
    # Need to stub log_debug since it's expected by the library
    log_debug() { :; }
    export -f log_debug

    source "$SCRIPT_DIR/hallucination_defense.sh"
}

teardown() {
    if [[ -n "$TEST_TEMP_DIR" ]] && [[ -d "$TEST_TEMP_DIR" ]]; then
        rm -rf "$TEST_TEMP_DIR"
    fi
}

assert_equals() {
    local expected="$1"
    local actual="$2"
    local message="${3:-}"

    if [[ "$expected" == "$actual" ]]; then
        return 0
    else
        printf '%sFAIL%s: Expected '\''%s'\'' but got '\''%s'\'' %s\n' "$RED" "$NC" "$expected" "$actual" "${message:+($message)}"
        return 1
    fi
}

assert_starts_with() {
    local prefix="$1"
    local actual="$2"
    local message="${3:-}"

    if [[ "$actual" == "$prefix"* ]]; then
        return 0
    else
        printf '%sFAIL%s: Expected to start with '\''%s'\'' but got '\''%s'\'' %s\n' "$RED" "$NC" "$prefix" "$actual" "${message:+($message)}"
        return 1
    fi
}

run_test() {
    local test_name="$1"
    local test_func="$2"

    TESTS_RUN=$((TESTS_RUN + 1))
    echo -n "  $test_name ... "

    if $test_func; then
        printf '%sPASS%s\n' "$GREEN" "$NC"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

#######################################
# Test: verify_quote
#######################################

test_verify_quote_exact_match() {
    local test_file="$TEST_TEMP_DIR/TestClass.java"
    cat > "$test_file" << 'EOF'
public class TestClass {
    public void doSomething() {
        String result = process(input);
        return result;
    }
}
EOF

    local result
    result=$(verify_quote "$test_file" 3 "String result = process(input);")
    assert_equals "MATCH:exact:3" "$result"
}

test_verify_quote_nearby_match() {
    local test_file="$TEST_TEMP_DIR/TestClass.java"
    cat > "$test_file" << 'EOF'
public class TestClass {
    public void doSomething() {
        String result = process(input);
        return result;
    }
}
EOF

    # Quote is at line 3, but we claim line 5 (within ±5 tolerance)
    local result
    result=$(verify_quote "$test_file" 5 "String result = process(input);")
    assert_equals "MATCH:nearby:3" "$result"
}

test_verify_quote_elsewhere() {
    local test_file="$TEST_TEMP_DIR/TestClass.java"
    cat > "$test_file" << 'EOF'
line 1
line 2
line 3
line 4
line 5
line 6
line 7
line 8
line 9
line 10
line 11
line 12
String result = process(input);
EOF

    # Quote is at line 13, but we claim line 1 (outside ±5 window)
    local result
    result=$(verify_quote "$test_file" 1 "String result = process(input);")
    assert_equals "MATCH:elsewhere:13" "$result"
}

test_verify_quote_no_match() {
    local test_file="$TEST_TEMP_DIR/TestClass.java"
    cat > "$test_file" << 'EOF'
public class TestClass {
    public void doSomething() {
        String result = process(input);
        return result;
    }
}
EOF

    local result
    result=$(verify_quote "$test_file" 3 "this code does not exist anywhere")
    assert_equals "NO_MATCH" "$result"
}

test_verify_quote_file_not_found() {
    local result
    result=$(verify_quote "/nonexistent/file.java" 1 "some code")
    assert_equals "NO_MATCH:file_not_found" "$result"
}

test_verify_quote_too_short() {
    local test_file="$TEST_TEMP_DIR/TestClass.java"
    echo "x" > "$test_file"

    local result
    result=$(verify_quote "$test_file" 1 "ab")
    assert_equals "NO_MATCH:quote_too_short" "$result"
}

test_verify_quote_whitespace_normalization() {
    local test_file="$TEST_TEMP_DIR/TestClass.java"
    # File has different whitespace than the quote
    printf 'String   result  =  process(input);\n' > "$test_file"

    local result
    result=$(verify_quote "$test_file" 1 "String result = process(input);")
    assert_equals "MATCH:exact:1" "$result"
}

#######################################
# Test: validate_indices
#######################################

test_validate_indices_valid() {
    # Set up FILE_INDEX
    local test_file="$TEST_TEMP_DIR/TestClass.java"
    printf 'line1\nline2\nline3\n' > "$test_file"

    FILE_INDEX=()
    FILE_INDEX[1]="$test_file"
    NUM_FILES=1

    local result
    result=$(validate_indices 1 2)
    assert_equals "VALID" "$result"
}

test_validate_indices_invalid_file_index() {
    FILE_INDEX=()
    FILE_INDEX[1]="somefile.java"
    NUM_FILES=1

    local result
    result=$(validate_indices 5 1)
    assert_starts_with "INVALID:file_index_out_of_range" "$result"
}

test_validate_indices_invalid_line_index() {
    local test_file="$TEST_TEMP_DIR/TestClass.java"
    printf 'line1\nline2\nline3\n' > "$test_file"

    FILE_INDEX=()
    FILE_INDEX[1]="$test_file"
    NUM_FILES=1

    local result
    result=$(validate_indices 1 100)
    assert_starts_with "INVALID:line_index_out_of_range" "$result"
}

test_validate_indices_non_numeric_file() {
    FILE_INDEX=()
    NUM_FILES=0

    local result
    result=$(validate_indices "abc" 1)
    assert_equals "INVALID:file_index_not_numeric" "$result"
}

test_validate_indices_non_numeric_line() {
    local test_file="$TEST_TEMP_DIR/TestClass.java"
    echo "line1" > "$test_file"

    FILE_INDEX=()
    FILE_INDEX[1]="$test_file"
    NUM_FILES=1

    local result
    result=$(validate_indices 1 "xyz")
    assert_equals "INVALID:line_index_not_numeric" "$result"
}

#######################################
# Test: compare_locations
#######################################

test_compare_locations_agree() {
    local result
    result=$(compare_locations 10 10 3)
    assert_equals "AGREE" "$result"
}

test_compare_locations_agree_within_tolerance() {
    local result
    result=$(compare_locations 10 12 3)
    assert_equals "AGREE" "$result"
}

test_compare_locations_disagree() {
    local result
    result=$(compare_locations 10 20 3)
    assert_equals "DISAGREE:discovery=10,reloc=20" "$result"
}

test_compare_locations_not_found_null() {
    local result
    result=$(compare_locations 10 "null" 3)
    assert_equals "NOT_FOUND" "$result"
}

test_compare_locations_not_found_empty() {
    local result
    result=$(compare_locations 10 "" 3)
    assert_equals "NOT_FOUND" "$result"
}

test_compare_locations_not_found_zero() {
    local result
    result=$(compare_locations 10 0 3)
    assert_equals "NOT_FOUND" "$result"
}

#######################################
# Test: resolve_indices
#######################################

test_resolve_indices_valid() {
    local test_file="$TEST_TEMP_DIR/TestClass.java"
    echo "content" > "$test_file"

    FILE_INDEX=()
    FILE_INDEX[1]="$test_file"

    local result
    result=$(resolve_indices 1 42)
    assert_equals "$test_file|42" "$result"
}

test_resolve_indices_invalid() {
    FILE_INDEX=()

    local result
    result=$(resolve_indices 99 42)
    assert_equals "" "$result"
}

#######################################
# Test: build_file_index
#######################################

test_build_file_index() {
    local file1="$TEST_TEMP_DIR/File1.java"
    local file2="$TEST_TEMP_DIR/File2.java"
    echo "content1" > "$file1"
    echo "content2" > "$file2"

    local files_list="$TEST_TEMP_DIR/files.txt"
    printf '%s\n%s\n' "$file1" "$file2" > "$files_list"

    build_file_index "$files_list"

    assert_equals 2 "$NUM_FILES" "NUM_FILES" && \
    assert_equals "$file1" "${FILE_INDEX[1]}" "FILE_INDEX[1]" && \
    assert_equals "$file2" "${FILE_INDEX[2]}" "FILE_INDEX[2]"
}

test_build_file_index_skips_nonexistent() {
    local file1="$TEST_TEMP_DIR/File1.java"
    echo "content1" > "$file1"

    local files_list="$TEST_TEMP_DIR/files.txt"
    printf '%s\n/nonexistent/file.java\n' "$file1" > "$files_list"

    build_file_index "$files_list"

    assert_equals 1 "$NUM_FILES" "Should skip nonexistent files"
}

#######################################
# Test: format_file_with_line_numbers
#######################################

test_format_file_with_line_numbers() {
    local test_file="$TEST_TEMP_DIR/TestClass.java"
    printf 'line1\nline2\nline3\n' > "$test_file"

    local result
    result=$(format_file_with_line_numbers "$test_file" 100)

    [[ "$result" == *"[001] line1"* ]] && \
    [[ "$result" == *"[002] line2"* ]] && \
    [[ "$result" == *"[003] line3"* ]]
}

test_format_file_with_line_numbers_truncation() {
    local test_file="$TEST_TEMP_DIR/TestClass.java"
    printf 'line1\nline2\nline3\nline4\nline5\n' > "$test_file"

    local result
    result=$(format_file_with_line_numbers "$test_file" 2)

    [[ "$result" == *"[001] line1"* ]] && \
    [[ "$result" == *"[002] line2"* ]] && \
    [[ "$result" == *"truncated"* ]] && \
    [[ "$result" != *"[003]"* ]]
}

#######################################
# Test: Metrics
#######################################

test_metrics_increment_and_print() {
    reset_hallucination_stats

    increment_stat "invalid_index"
    increment_stat "invalid_index"
    increment_stat "quote_rejected"
    increment_stat "passed"
    increment_stat "passed"
    increment_stat "passed"

    assert_equals 2 "$HALLUCINATION_STATS_INVALID_INDEX" && \
    assert_equals 1 "$HALLUCINATION_STATS_QUOTE_REJECTED" && \
    assert_equals 3 "$HALLUCINATION_STATS_PASSED"
}

#######################################
# Main
#######################################

main() {
    echo "========================================"
    echo "Hallucination Defense Library Tests"
    echo "========================================"
    echo ""

    setup
    trap teardown EXIT

    echo "verify_quote tests:"
    run_test "exact match" test_verify_quote_exact_match
    run_test "nearby match" test_verify_quote_nearby_match
    run_test "elsewhere match" test_verify_quote_elsewhere
    run_test "no match" test_verify_quote_no_match
    run_test "file not found" test_verify_quote_file_not_found
    run_test "quote too short" test_verify_quote_too_short
    run_test "whitespace normalization" test_verify_quote_whitespace_normalization
    echo ""

    echo "validate_indices tests:"
    run_test "valid indices" test_validate_indices_valid
    run_test "invalid file index" test_validate_indices_invalid_file_index
    run_test "invalid line index" test_validate_indices_invalid_line_index
    run_test "non-numeric file" test_validate_indices_non_numeric_file
    run_test "non-numeric line" test_validate_indices_non_numeric_line
    echo ""

    echo "compare_locations tests:"
    run_test "agree exact" test_compare_locations_agree
    run_test "agree within tolerance" test_compare_locations_agree_within_tolerance
    run_test "disagree" test_compare_locations_disagree
    run_test "not found (null)" test_compare_locations_not_found_null
    run_test "not found (empty)" test_compare_locations_not_found_empty
    run_test "not found (zero)" test_compare_locations_not_found_zero
    echo ""

    echo "resolve_indices tests:"
    run_test "valid resolution" test_resolve_indices_valid
    run_test "invalid resolution" test_resolve_indices_invalid
    echo ""

    echo "build_file_index tests:"
    run_test "basic indexing" test_build_file_index
    run_test "skips nonexistent" test_build_file_index_skips_nonexistent
    echo ""

    echo "format_file_with_line_numbers tests:"
    run_test "basic formatting" test_format_file_with_line_numbers
    run_test "truncation" test_format_file_with_line_numbers_truncation
    echo ""

    echo "metrics tests:"
    run_test "increment and stats" test_metrics_increment_and_print
    echo ""

    echo "========================================"
    echo "Results: $TESTS_PASSED/$TESTS_RUN passed"
    if [[ $TESTS_FAILED -gt 0 ]]; then
        printf '%s%d test(s) failed%s\n' "$RED" "$TESTS_FAILED" "$NC"
        echo "========================================"
        exit 1
    else
        printf '%sAll tests passed!%s\n' "$GREEN" "$NC"
        echo "========================================"
        exit 0
    fi
}

main "$@"
