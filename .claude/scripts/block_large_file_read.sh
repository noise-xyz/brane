#!/bin/bash
# PreToolUse hook: Block ALL reads of large Java files - force LSP usage

# Read stdin once and save it
input=$(cat)

file=$(echo "$input" | jq -r '.tool_input.file_path')

# Only check Java files
if echo "$file" | grep -q '\.java$'; then
    # Check if file exists
    if [ ! -f "$file" ]; then
        exit 0
    fi

    lines=$(wc -l < "$file" 2>/dev/null | tr -d ' ' || echo 0)

    # Block ALL reads of large Java files (>500 lines)
    if [ "$lines" -gt 500 ]; then
        echo "BLOCKED: Large Java file (${lines} lines). Reading is disabled." >&2
        echo "" >&2
        echo "You MUST use cclsp MCP tools instead:" >&2
        echo "  mcp__cclsp__find_definition(file_path, symbol_name)" >&2
        echo "  mcp__cclsp__find_references(file_path, symbol_name)" >&2
        echo "  mcp__cclsp__get_diagnostics(file_path)" >&2
        echo "" >&2
        echo "Use Grep to find line numbers, then use LSP tools to navigate." >&2
        exit 2
    fi
fi

exit 0
