#!/bin/bash
# PreToolUse hook: Block FULL reads of large Java files - force LSP usage
# Files explored via LSP tools are allowed (tracked in .lsp-explored state file)

# Ensure clean session state (cleans state files on new session)
"${CLAUDE_PROJECT_DIR:-.}/.claude/scripts/ensure_clean_session.sh" 2>/dev/null

# Read stdin once and save it
input=$(cat)

file=$(echo "$input" | jq -r '.tool_input.file_path')
offset=$(echo "$input" | jq -r '.tool_input.offset // empty')
limit=$(echo "$input" | jq -r '.tool_input.limit // empty')

# Only check Java files
if echo "$file" | grep -q '\.java$'; then
    # Check if file exists
    if [ ! -f "$file" ]; then
        exit 0
    fi

    # Resolve to absolute path for comparison with state file
    abs_file="$file"
    if [[ ! "$file" = /* ]]; then
        abs_file="$(cd "$CLAUDE_PROJECT_DIR" 2>/dev/null && pwd)/$file"
    fi
    # Normalize the path
    abs_file=$(cd "$(dirname "$abs_file")" 2>/dev/null && pwd)/$(basename "$abs_file") 2>/dev/null || echo "$abs_file"

    # Check if file has been LSP-explored
    state_file="${CLAUDE_PROJECT_DIR:-.}/.claude/.lsp-explored"
    if [ -f "$state_file" ] && grep -qxF "$abs_file" "$state_file" 2>/dev/null; then
        # File was explored via LSP - allow read
        exit 0
    fi

    # Allow partial reads (when offset or limit is specified)
    # This enables Edit tool to work after LSP-assisted understanding
    if [ -n "$offset" ] || [ -n "$limit" ]; then
        exit 0
    fi

    lines=$(wc -l < "$file" 2>/dev/null | tr -d ' ' || echo 0)

    # Block FULL reads of large Java files (>500 lines)
    if [ "$lines" -gt 500 ]; then
        echo "BLOCKED: Large Java file (${lines} lines). Full reads are disabled." >&2
        echo "" >&2
        echo "You MUST use cclsp MCP tools first to explore the file:" >&2
        echo "  mcp__cclsp__find_definition(file_path, symbol_name)" >&2
        echo "  mcp__cclsp__find_references(file_path, symbol_name)" >&2
        echo "  mcp__cclsp__get_diagnostics(file_path)" >&2
        echo "" >&2
        echo "After using LSP tools, the file will be unlocked for reading." >&2
        exit 2
    fi
fi

exit 0
