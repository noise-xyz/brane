#!/bin/bash
# Validates tasks.txt files for compatibility with run_todo_fixes.sh
# Called from Claude hook on Edit/Write to *tasks*.txt files
#
# Expected format:
#   - Empty lines: skipped
#   - Comments: lines starting with # (with optional leading whitespace)
#   - Tasks: TASK-ID:Description (ID and description must not be empty)

set -e

# Read file path from hook input
FILE_PATH=$(jq -r '.tool_input.file_path')

# Only validate files with 'tasks' in the name and .txt extension
if ! echo "$FILE_PATH" | grep -qiE 'tasks[^/]*\.txt$'; then
    exit 0
fi

# Check if file exists
if [[ ! -f "$FILE_PATH" ]]; then
    exit 0
fi

ERRORS=""
LINE_NUM=0

while IFS= read -r line || [[ -n "$line" ]]; do
    LINE_NUM=$((LINE_NUM + 1))

    # Skip empty lines
    [[ -z "$line" ]] && continue

    # Skip comment lines (# with optional leading whitespace)
    [[ "$line" =~ ^[[:space:]]*# ]] && continue

    # Must be a task line - validate format TASK-ID:Description
    if ! echo "$line" | grep -q ':'; then
        ERRORS="${ERRORS}Line $LINE_NUM: Missing colon separator. Expected 'TASK-ID:Description'\n"
        continue
    fi

    # Extract task ID (everything before first colon)
    task_id="${line%%:*}"

    # Extract description (everything after first colon)
    task_desc="${line#*:}"

    # Validate task ID is not empty or whitespace only
    if [[ -z "$task_id" || "$task_id" =~ ^[[:space:]]*$ ]]; then
        ERRORS="${ERRORS}Line $LINE_NUM: Empty task ID before colon\n"
    fi

    # Validate description is not empty or whitespace only
    if [[ -z "$task_desc" || "$task_desc" =~ ^[[:space:]]*$ ]]; then
        ERRORS="${ERRORS}Line $LINE_NUM: Empty description after colon for task '$task_id'\n"
    fi

done < "$FILE_PATH"

# Report errors if any
if [[ -n "$ERRORS" ]]; then
    # Output warning as system message
    ESCAPED_ERRORS=$(printf '%s' "$ERRORS" | sed 's/\\n/\\\\n/g' | tr '\n' ' ')
    echo "{\"systemMessage\": \"Tasks file validation warnings:\\n${ESCAPED_ERRORS}Format required by run_todo_fixes.sh: TASK-ID:Description\"}"
fi

exit 0
