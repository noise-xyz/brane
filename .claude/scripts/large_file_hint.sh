#!/bin/bash
# PostToolUse hook: Hint about LSP tools when reading large Java files with offset/limit

file=$(jq -r '.tool_input.file_path')

# Only check Java files
if echo "$file" | grep -q '\.java$'; then
    if [ -f "$file" ]; then
        lines=$(wc -l < "$file" 2>/dev/null | tr -d ' ' || echo 0)
        if [ "$lines" -gt 500 ]; then
            echo "{\"systemMessage\": \"Tip: cclsp MCP tools available: definition(), references(), hover()\"}"
        fi
    fi
fi

exit 0
