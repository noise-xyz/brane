#!/bin/bash
# Ensures state files are cleaned at the start of each Claude Code session
# Called by other hooks to check if session has changed

state_dir="${CLAUDE_PROJECT_DIR:-.}/.claude"
session_file="$state_dir/.session-id"

# Get current session ID
# PPID is the Claude Code process, use its PID + start time as unique session ID
if [ -n "$CLAUDE_SESSION_ID" ]; then
    current_session="$CLAUDE_SESSION_ID"
elif [ -n "$PPID" ]; then
    # Get Claude process start time (unique per terminal, stable within session)
    claude_start=$(ps -p "$PPID" -o lstart= 2>/dev/null | tr ' ' '-')
    if [ -n "$claude_start" ]; then
        current_session="${PPID}-${claude_start}"
    else
        current_session="ppid-${PPID}"
    fi
else
    current_session="unknown-$(date +%s)"
fi

# Check if session changed
if [ -f "$session_file" ]; then
    stored_session=$(cat "$session_file" 2>/dev/null)
    if [ "$stored_session" = "$current_session" ]; then
        # Same session, no cleanup needed
        exit 0
    fi
fi

# New session - clean state files
rm -f "$state_dir/.lsp-explored" 2>/dev/null
rm -f "$state_dir/.pending-ref-check" 2>/dev/null
rm -f "$state_dir/.verified-refs" 2>/dev/null

# Store new session ID
mkdir -p "$state_dir" 2>/dev/null
echo "$current_session" > "$session_file"

exit 0
