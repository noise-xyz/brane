#!/bin/bash
# PostToolUse hook: Enforce downstream impact checking after editing public methods/classes
# Tracks symbols that were edited and haven't had references checked yet

input=$(cat)
file=$(printf '%s' "$input" | jq -r '.tool_input.file_path')
old_string=$(printf '%s' "$input" | jq -r '.tool_input.old_string // empty')
new_string=$(printf '%s' "$input" | jq -r '.tool_input.new_string // empty')

# Only check Java source files (not tests)
if ! printf '%s' "$file" | grep -q '\.java$' || ! printf '%s' "$file" | grep -q 'src/main/'; then
    exit 0
fi

# Check if editing public API (method or class/interface/etc)
if ! printf '%s' "$old_string" | grep -qE 'public[[:space:]]+(class|interface|record|enum|static|abstract|final|synchronized|[a-zA-Z])'; then
    exit 0
fi

# Extract the symbol name
# Try class/interface/record/enum first
symbol=$(printf '%s' "$old_string" | grep -oE '(class|interface|record|enum)[[:space:]]+[A-Z][a-zA-Z0-9]+' | awk '{print $2}' | head -1)
symbol_type="class"

# If not a type declaration, try to extract method name
if [ -z "$symbol" ]; then
    symbol=$(printf '%s' "$old_string" | grep -oE '[[:space:]][a-z][a-zA-Z0-9]*[[:space:]]*\(' | head -1 | sed 's/[[:space:]]*($//; s/^[[:space:]]*//')
    symbol_type="method"
fi

if [ -z "$symbol" ]; then
    exit 0
fi

# State file for tracking symbols that need reference checking
state_dir="${CLAUDE_PROJECT_DIR:-.}/.claude"
pending_file="$state_dir/.pending-ref-check"
verified_file="$state_dir/.verified-refs"

mkdir -p "$state_dir" 2>/dev/null

# Normalize file path
abs_file="$file"
if [[ ! "$file" = /* ]]; then
    abs_file="$(cd "$CLAUDE_PROJECT_DIR" 2>/dev/null && pwd)/$file"
fi
abs_file=$(cd "$(dirname "$abs_file")" 2>/dev/null && pwd)/$(basename "$abs_file") 2>/dev/null || echo "$abs_file"

# Create entry: file|symbol|symbol_type
entry="${abs_file}|${symbol}|${symbol_type}"

# Check if this symbol's references were already verified this session
if [ -f "$verified_file" ] && grep -qxF "$entry" "$verified_file" 2>/dev/null; then
    # Already verified - just inform
    echo "{\"systemMessage\": \"Edited public $symbol_type '$symbol' (references were previously verified).\"}"
    exit 0
fi

# Add to pending check list
if [ -f "$pending_file" ]; then
    if ! grep -qxF "$entry" "$pending_file" 2>/dev/null; then
        echo "$entry" >> "$pending_file"
    fi
else
    echo "$entry" > "$pending_file"
fi

# Check if signature changed (not just implementation)
signature_changed=false
# Extract method signature patterns
old_sig=$(printf '%s' "$old_string" | grep -oE 'public[^{;]+' | head -1)
new_sig=$(printf '%s' "$new_string" | grep -oE 'public[^{;]+' | head -1)

if [ -n "$old_sig" ] && [ -n "$new_sig" ] && [ "$old_sig" != "$new_sig" ]; then
    signature_changed=true
fi

# Build the system message
if [ "$signature_changed" = true ]; then
    msg="SIGNATURE CHANGE DETECTED for public $symbol_type '$symbol'.\\n\\n"
    msg+="This is a BREAKING CHANGE. You MUST check downstream effects:\\n\\n"
    msg+="1. LSP (preferred):\\n"
    msg+="   mcp__cclsp__find_references(file_path=\\\"$abs_file\\\", symbol_name=\\\"$symbol\\\")\\n\\n"
    msg+="2. Grep (fallback if LSP returns only self-references):\\n"
    msg+="   Grep(pattern=\\\"$symbol\\\", path=\\\"src/main/java\\\")\\n\\n"
    msg+="After checking references, update all affected callers before proceeding."
else
    msg="Edited public $symbol_type '$symbol'.\\n\\n"
    msg+="Recommended: Check for downstream effects:\\n\\n"
    msg+="1. LSP (preferred):\\n"
    msg+="   mcp__cclsp__find_references(file_path=\\\"$abs_file\\\", symbol_name=\\\"$symbol\\\")\\n\\n"
    msg+="2. Grep (fallback if LSP returns only self-references):\\n"
    msg+="   Grep(pattern=\\\"$symbol\\\", path=\\\"src/main/java\\\")\\n\\n"
    msg+="Run these to verify no callers are affected."
fi

echo "{\"systemMessage\": \"$msg\"}"
exit 0
