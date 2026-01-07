#!/bin/bash
# Autonomous sequential Claude task runner
# Reads tasks from a file and runs Claude to fix each one

set -e

# Configuration
TASKS_FILE="${1:-tasks.txt}"
LOG_DIR="fix_logs"
TODO_FILE="TODO.md"

# Auto-detect claude command (claude-tty for antigravity, claude otherwise)
if command -v claude-tty &> /dev/null; then
    CLAUDE_CMD="claude-tty"
elif command -v claude &> /dev/null; then
    CLAUDE_CMD="claude"
else
    echo "Error: Neither claude-tty nor claude found in PATH"
    exit 1
fi

mkdir -p "$LOG_DIR"

# Detect module from current branch name (e.g., fix/brane-rpc -> rpc)
detect_module() {
    local branch
    branch=$(git branch --show-current 2>/dev/null || echo "main")
    if [[ "$branch" =~ brane-([a-z]+) ]]; then
        echo "${BASH_REMATCH[1]}"
    else
        echo ""
    fi
}

MODULE=$(detect_module)

# Mark task as done in TODO.md
mark_done() {
    local task_id="$1"
    if [[ -f "$TODO_FILE" ]]; then
        sed -i '' "s/### ${task_id}:/### ${task_id}: [DONE]/" "$TODO_FILE" 2>/dev/null || true
        git add "$TODO_FILE" 2>/dev/null || true
        git commit -m "docs: mark ${task_id} as complete" --no-verify 2>/dev/null || true
    fi
}

# Build the prompt with all permissions pre-granted
build_prompt() {
    local task_id="$1"
    local task_desc="$2"

    cat <<EOF
You are autonomously fixing issue **$task_id** from a code review.

## Task
**ID:** $task_id
**Description:** $task_desc
${MODULE:+**Module:** brane-$MODULE}

## Workflow
1. Read and understand the relevant file(s) and surrounding context
2. Make the minimal, focused fix as described
3. Run ONLY the relevant unit tests (not full suite): ./gradlew test --tests "*ClassName*"
4. Commit with: git add -A && git commit -m "<type>(<scope>): <desc> [$task_id]"

## Pre-Approved Permissions (DO NOT ASK)
- Edit any file in the codebase
- Kill/restart anvil processes
- Run any gradle commands or tests
- Create new files if needed
- Git operations (add, commit, push)
- Access to all tools without confirmation

## Commit Convention
- Format: <type>(<scope>): <description> [$task_id]
- Type: fix, feat, refactor, docs, test, perf
- Scope: Detect from file path (e.g., rpc, core, contract)
- Example: fix(rpc): handle null response [$task_id]

## Rules
- Focus ONLY on this specific issue
- Do NOT refactor unrelated code
- Do NOT add features beyond the fix
- If tests fail, fix them
- If blocked, document why and move on

Begin by reading the relevant files.
EOF
}

# Check if tasks file exists
if [[ ! -f "$TASKS_FILE" ]]; then
    echo "Usage: $0 [tasks_file]"
    echo ""
    echo "Tasks file format (one task per line):"
    echo "  TASK-ID:Description of what to fix"
    echo ""
    echo "Example:"
    echo "  HIGH-1:Fix null pointer in DefaultClient.java:42"
    echo "  MED-2:Add validation to parseResponse method"
    echo ""
    echo "No tasks file found at: $TASKS_FILE"
    exit 1
fi

# Count tasks
TOTAL=$(grep -c ':' "$TASKS_FILE" 2>/dev/null || echo 0)

echo "========================================"
echo "Autonomous Claude Task Runner"
echo "========================================"
echo "Tasks file: $TASKS_FILE"
echo "Total tasks: $TOTAL"
echo "Module: ${MODULE:-auto-detect}"
echo "Claude cmd: $CLAUDE_CMD"
echo "Logs: $LOG_DIR/"
echo "========================================"
echo ""

# Process each task
TASK_NUM=0
while IFS=':' read -r task_id task_desc || [[ -n "$task_id" ]]; do
    # Skip empty lines and comments
    [[ -z "$task_id" || "$task_id" =~ ^[[:space:]]*# ]] && continue

    TASK_NUM=$((TASK_NUM + 1))
    log_file="$LOG_DIR/${task_id}.log"
    timestamp=$(date '+%Y-%m-%d %H:%M:%S')

    echo "[$timestamp] Task $TASK_NUM/$TOTAL: $task_id"
    echo "Description: $task_desc"
    echo "Log: $log_file"
    echo ""

    prompt=$(build_prompt "$task_id" "$task_desc")

    if $CLAUDE_CMD -p "$prompt" --dangerously-skip-permissions 2>&1 | tee "$log_file"; then
        echo ""
        echo "[$task_id] Completed"
        mark_done "$task_id"
    else
        echo ""
        echo "[$task_id] Failed - check $log_file"
    fi

    echo ""
    echo "----------------------------------------"
    echo ""

done < "$TASKS_FILE"

echo "========================================"
echo "All tasks processed. Running final verification..."
echo "========================================"

# Final verification - run full test suite once
$CLAUDE_CMD -p "Run ./gradlew test to verify all fixes. If any tests fail, fix them." --dangerously-skip-permissions 2>&1 | tee "$LOG_DIR/final_verification.log"

echo "========================================"
echo "Run complete: $TASK_NUM tasks processed"
echo "========================================"
