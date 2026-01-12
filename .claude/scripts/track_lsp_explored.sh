#!/bin/bash
# PreToolUse hook: Track files explored via LSP tools AND verify reference checks
# - Records file paths so block_large_file_read.sh can allow reads
# - Marks symbols as verified when find_references is called (clears pending ref checks)

# Ensure clean session state (cleans state files on new session)
"${CLAUDE_PROJECT_DIR:-.}/.claude/scripts/ensure_clean_session.sh" 2>/dev/null

input=$(cat)

tool_name=$(echo "$input" | jq -r '.tool_name // empty')
file_path=$(echo "$input" | jq -r '.tool_input.file_path // empty')
symbol_name=$(echo "$input" | jq -r '.tool_input.symbol_name // empty')

if [ -z "$file_path" ]; then
    exit 0
fi

# Resolve to absolute path if relative
if [[ ! "$file_path" = /* ]]; then
    file_path="$(cd "$CLAUDE_PROJECT_DIR" 2>/dev/null && pwd)/$file_path"
fi

# Normalize the path (resolve symlinks, remove ..)
file_path=$(cd "$(dirname "$file_path")" 2>/dev/null && pwd)/$(basename "$file_path") 2>/dev/null || echo "$file_path"

# State file locations
state_dir="${CLAUDE_PROJECT_DIR:-.}/.claude"
explored_file="$state_dir/.lsp-explored"
pending_file="$state_dir/.pending-ref-check"
verified_file="$state_dir/.verified-refs"

# Ensure state directory exists
mkdir -p "$state_dir" 2>/dev/null

# === Track explored files (for any LSP tool) ===
if [ -f "$explored_file" ]; then
    if ! grep -qxF "$file_path" "$explored_file" 2>/dev/null; then
        echo "$file_path" >> "$explored_file"
    fi
else
    echo "$file_path" > "$explored_file"
fi

# === Track verified references (specifically for find_references) ===
if [ "$tool_name" = "mcp__cclsp__find_references" ] && [ -n "$symbol_name" ]; then
    # Create the entry format matching post_edit_impact.sh
    # We need to check both method and class variants since we don't know which it was
    entry_method="${file_path}|${symbol_name}|method"
    entry_class="${file_path}|${symbol_name}|class"

    # Add to verified list
    touch "$verified_file"

    # Check and add method entry
    if ! grep -qxF "$entry_method" "$verified_file" 2>/dev/null; then
        echo "$entry_method" >> "$verified_file"
    fi

    # Check and add class entry
    if ! grep -qxF "$entry_class" "$verified_file" 2>/dev/null; then
        echo "$entry_class" >> "$verified_file"
    fi

    # Remove from pending list if present
    if [ -f "$pending_file" ]; then
        # Create temp file without the verified entries
        grep -vxF "$entry_method" "$pending_file" 2>/dev/null | grep -vxF "$entry_class" > "$pending_file.tmp" 2>/dev/null
        mv "$pending_file.tmp" "$pending_file" 2>/dev/null || rm -f "$pending_file.tmp"

        # Remove pending file if empty
        if [ ! -s "$pending_file" ]; then
            rm -f "$pending_file"
        fi
    fi
fi

exit 0
