#!/bin/bash
# PreToolUse hook: Warn about impact before editing public methods/classes

input=$(cat)
file=$(echo "$input" | jq -r '.tool_input.file_path')
old_string=$(echo "$input" | jq -r '.tool_input.old_string // empty')

# Only check Java source files (not tests)
if echo "$file" | grep -q '\.java$' && echo "$file" | grep -q '/src/main/'; then
    # Check if editing public API
    if echo "$old_string" | grep -qE 'public\s+(class|interface|record|enum|static|[a-zA-Z])'; then
        # Try to extract symbol name
        symbol=$(echo "$old_string" | grep -oE '(class|interface|record|enum)\s+[A-Z][a-zA-Z0-9]+' | awk '{print $2}' | head -1)
        if [ -z "$symbol" ]; then
            symbol=$(echo "$old_string" | grep -oE '\s[a-z][a-zA-Z0-9]+\s*\(' | sed 's/\s*(//;s/^\s*//' | head -1)
        fi

        if [ -n "$symbol" ]; then
            echo "{\"systemMessage\": \"Editing public API: $symbol. Consider running mcp__cclsp__find_references() to check impact.\"}"
        fi
    fi
fi

exit 0
