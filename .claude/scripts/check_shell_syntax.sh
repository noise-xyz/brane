#!/bin/bash
# Hook script to validate shell script syntax after Edit/Write
# Receives tool input JSON via stdin from Claude Code hooks
#
# Checks:
# 1. Bash syntax (bash -n)
# 2. macOS/BSD compatibility issues
# 3. Common portability problems

set -o pipefail

# Read the file path from JSON input
FILE_PATH=$(jq -r '.tool_input.file_path' 2>/dev/null)

# Only check .sh files
if [[ ! "$FILE_PATH" =~ \.sh$ ]]; then
    exit 0
fi

# Skip self-check (this script contains pattern strings that would false-positive)
SCRIPT_NAME=$(basename "$FILE_PATH")
if [[ "$SCRIPT_NAME" == "check_shell_syntax.sh" ]]; then
    exit 0
fi

# Check if file exists
if [[ ! -f "$FILE_PATH" ]]; then
    exit 0
fi

ERRORS=""

# Helper to add error
add_error() {
    if [[ -n "$ERRORS" ]]; then
        ERRORS="$ERRORS; $1"
    else
        ERRORS="$1"
    fi
}

#######################################
# Check 1: Bash syntax check
#######################################
BASH_ERROR=$(bash -n "$FILE_PATH" 2>&1)
if [[ $? -ne 0 ]]; then
    add_error "Bash syntax error: $(echo "$BASH_ERROR" | head -3 | tr '\n' ' ')"
fi

#######################################
# Check 2: macOS/BSD compatibility
#######################################

# Detect OS
IS_MACOS=false
if [[ "$(uname)" == "Darwin" ]]; then
    IS_MACOS=true
fi

# Note: We grep directly on file for efficiency (no need to cat into variable)

# Check for GNU sed -i syntax that breaks on macOS
# GNU: sed -i 's/...'  BSD: sed -i '' 's/...'
# Match sed -i followed by a pattern (not followed by '' or "")
if grep -qE "sed\s+-i\s+['\"]" "$FILE_PATH" && ! grep -qE "sed\s+-i\s+['\"]['\"]" "$FILE_PATH"; then
    if $IS_MACOS; then
        add_error "sed -i without '' breaks on macOS. Use: sed -i '' 's/.../' or use a temp file"
    fi
fi

# Check for GNU-only sed flags
if grep -qE "sed\s+(-r|--regexp-extended)" "$FILE_PATH"; then
    add_error "sed -r is GNU-only. Use sed -E for portability (works on both macOS and Linux)"
fi

# Check for bash 4.x+ features that break on macOS default bash (3.2)
# Associative arrays: declare -A
if grep -qE "declare\s+-A" "$FILE_PATH"; then
    if $IS_MACOS; then
        add_error "declare -A (associative arrays) requires bash 4.x+. macOS default is bash 3.2. Use /opt/homebrew/bin/bash or refactor"
    fi
fi

# readarray/mapfile (bash 4.x+)
if grep -qE "^\s*(readarray|mapfile)\s" "$FILE_PATH"; then
    if $IS_MACOS; then
        add_error "readarray/mapfile requires bash 4.x+. macOS default is bash 3.2. Use 'while read' loop instead"
    fi
fi

# &>> redirect (bash 4.x+)
if grep -qE "&>>" "$FILE_PATH"; then
    if $IS_MACOS; then
        add_error "&>> redirect requires bash 4.x+. Use >> file 2>&1 instead"
    fi
fi

# |& pipe (bash 4.x+)
if grep -qE "\|\&[^&]" "$FILE_PATH"; then
    if $IS_MACOS; then
        add_error "|& pipe requires bash 4.x+. Use 2>&1 | instead"
    fi
fi

# Check for GNU grep flags
if grep -qE "grep\s+(-P|--perl-regexp)" "$FILE_PATH"; then
    add_error "grep -P is GNU-only. Use grep -E for extended regex or install pcre on macOS"
fi

# Check for GNU xargs flags
if grep -qE "xargs\s+-d" "$FILE_PATH"; then
    add_error "xargs -d is GNU-only. Use tr or other methods on macOS"
fi

# Check for GNU date syntax
if grep -qE "date\s+-d" "$FILE_PATH"; then
    if $IS_MACOS; then
        add_error "date -d is GNU-only. On macOS use: date -j -f 'format' 'string'"
    fi
fi

# Check for GNU stat syntax
if grep -qE "stat\s+-c" "$FILE_PATH"; then
    if $IS_MACOS; then
        add_error "stat -c is GNU-only. On macOS use: stat -f 'format'"
    fi
fi

# Check for GNU find -printf
if grep -qE "find\s+.*-printf" "$FILE_PATH"; then
    if $IS_MACOS; then
        add_error "find -printf is GNU-only. On macOS use: find ... -exec stat -f '...' {} \\;"
    fi
fi

# Check for GNU head/tail with -c for bytes
if grep -qE "(head|tail)\s+-c\s*-" "$FILE_PATH"; then
    add_error "head/tail -c with negative numbers is GNU-only"
fi

# Check for readlink -f (GNU-only)
if grep -qE "readlink\s+-f" "$FILE_PATH"; then
    if $IS_MACOS; then
        add_error "readlink -f is GNU-only. On macOS use: greadlink -f (brew install coreutils) or python -c"
    fi
fi

# Check for realpath command (not on macOS by default)
if grep -qE "^\s*realpath\s" "$FILE_PATH" || grep -qE "\$\(realpath\s" "$FILE_PATH"; then
    if $IS_MACOS && ! command -v realpath &>/dev/null; then
        add_error "realpath not available on macOS. Use: greadlink -f or brew install coreutils"
    fi
fi

# Check for seq command (use brace expansion or jot on macOS)
if grep -qE "\bseq\s" "$FILE_PATH"; then
    if $IS_MACOS; then
        add_error "seq may not be available on macOS. Use: {1..N} brace expansion, jot, or install coreutils"
    fi
fi

# Check for tac command (not on macOS)
if grep -qE "\btac\s" "$FILE_PATH" || grep -qE "\btac$" "$FILE_PATH"; then
    if $IS_MACOS; then
        add_error "tac not available on macOS. Use: tail -r instead"
    fi
fi

# Check for timeout command (not on macOS)
# Exclude comments and string literals from check
if grep -v '^\s*#' "$FILE_PATH" | grep -v 'command -v' | grep -qE "\btimeout\s"; then
    if $IS_MACOS && ! command -v timeout &>/dev/null; then
        add_error "timeout not available on macOS. Use: gtimeout (brew install coreutils)"
    fi
fi

# Check for sort -V (version sort, GNU-only)
if grep -qE "sort\s+.*-V" "$FILE_PATH" || grep -qE "sort\s+-V" "$FILE_PATH"; then
    if $IS_MACOS; then
        add_error "sort -V (version sort) is GNU-only. No direct macOS equivalent"
    fi
fi

# Check for echo -e (behavior differs, use printf instead)
if grep -qE '\becho\s+-e\s' "$FILE_PATH"; then
    add_error "echo -e behavior varies. Use printf for portable escape sequences"
fi

# Check for GNU tar --transform (not on macOS)
if grep -qE "tar\s+.*--transform" "$FILE_PATH"; then
    if $IS_MACOS; then
        add_error "tar --transform is GNU-only. On macOS use: -s '/old/new/' or bsdtar"
    fi
fi

# Check for cp -T (GNU-only, no-target-directory)
if grep -qE "\bcp\s+.*-T\b" "$FILE_PATH"; then
    if $IS_MACOS; then
        add_error "cp -T is GNU-only. Restructure cp command for macOS compatibility"
    fi
fi

# Check for ln -T (GNU-only)
if grep -qE "\bln\s+.*-T\b" "$FILE_PATH"; then
    if $IS_MACOS; then
        add_error "ln -T is GNU-only. Restructure ln command for macOS compatibility"
    fi
fi

#######################################
# Check 3: Common issues
#######################################

# Check shebang
SHEBANG=$(head -1 "$FILE_PATH")
if [[ ! "$SHEBANG" =~ ^#! ]]; then
    add_error "Missing shebang (e.g., #!/bin/bash)"
elif [[ "$SHEBANG" =~ ^#!/bin/sh ]] && grep -qE "\[\[|\(\(|declare|local|function\s" "$FILE_PATH"; then
    add_error "Using bash features but shebang is #!/bin/sh. Use #!/bin/bash or #!/usr/bin/env bash"
fi

#######################################
# Output result
#######################################

if [[ -n "$ERRORS" ]]; then
    # Escape for JSON
    ERRORS_ESCAPED=$(echo "$ERRORS" | sed 's/"/\\"/g' | tr '\n' ' ')
    echo "{\"decision\": \"block\", \"reason\": \"Shell script issues in $FILE_PATH: $ERRORS_ESCAPED\"}"
fi

exit 0
