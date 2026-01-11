# Code Review Orchestrator v7.1

Automated code review using a multi-model LLM pipeline with layered hallucination defense.

## Overview

The orchestrator runs a multi-phase code review:

```
Discovery (Haiku) → Index Validation → Quote Verification → Re-localization (Sonnet) → Verification (Sonnet) → Opus Review
```

### Hallucination Defense Layers

| Layer | Defense | What It Catches |
|-------|---------|-----------------|
| 1 | Enumerated Indices | Forces `[N]` file and `[NNN]` line references - impossible to invent paths |
| 2 | Quote Verification | Requires verbatim code quotes that must match source file |
| 3 | Re-localization | Independent agent (different model) must locate the same issue |

## Requirements

- **bash 4.2+** (macOS: `brew install bash`)
- **jq** (macOS: `brew install jq`)
- **bc** (usually pre-installed)
- **coreutils** (provides `gtimeout` on macOS; Linux has `timeout` by default)
- **flock** (optional, for faster file locking; macOS: `brew install util-linux`)
- **claude CLI** (Anthropic's Claude Code)

### macOS Setup

```bash
# Install bash 4+ and dependencies
brew install bash jq coreutils

# Optional: Install util-linux for flock (faster file locking)
brew install util-linux
export PATH="/opt/homebrew/opt/util-linux/bin:$PATH"

# Run with newer bash
/opt/homebrew/bin/bash .claude/scripts/code_review_orchestrator_v7.sh main
```

### Linux Setup

```bash
# Most dependencies are pre-installed, just ensure:
sudo apt-get install jq bc  # Debian/Ubuntu
# or
sudo yum install jq bc      # RHEL/CentOS
```

## Usage

### Basic Usage

```bash
# Review changes against main branch
./.claude/scripts/code_review_orchestrator_v7.sh main

# Review against a different base branch
./.claude/scripts/code_review_orchestrator_v7.sh develop
```

### Options

```bash
# Enable debug output
DEBUG=true ./.claude/scripts/code_review_orchestrator_v7.sh main

# JSON structured logging (for CI pipelines)
./.claude/scripts/code_review_orchestrator_v7.sh main --json-log

# Disable expensive re-localization layer (faster, less accurate)
./.claude/scripts/code_review_orchestrator_v7.sh main --no-reloc

# Disable quote verification
./.claude/scripts/code_review_orchestrator_v7.sh main --no-quote-verify

# Use custom config file
./.claude/scripts/code_review_orchestrator_v7.sh main --config my_config.json
```

### Configuration File

Create `.claude/orchestrator.json` (loaded automatically) or specify with `--config`:

```json
{
  "max_parallel_discovery": 4,
  "max_parallel_verify": 4,
  "max_parallel_reloc": 6,
  "max_findings_to_verify": 30,
  "max_total_budget_usd": 5.0,
  "model_discovery": "haiku",
  "model_reloc": "sonnet",
  "model_verify": "sonnet",
  "enable_opus_review": true,
  "enable_quote_verification": true,
  "enable_relocalization": true,
  "reloc_tolerance": 3,
  "api_timeout": 120
}
```

**Config validation**: All config values are validated at load time. Invalid values will cause the script to exit with an error message.

## Output

### Generated Files

| File | Description |
|------|-------------|
| `spec/code_review.md` | Human-readable review report with findings by tier |
| `spec/tasks.txt` | Machine-parseable task list for CI integration |

### Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success, no issues found |
| 1 | Success, findings reported |
| 2 | Error during execution |
| 3 | Budget exceeded |
| 4 | Partial failure (some agents failed) |
| 5 | Missing prerequisites |

### Severity Tiers

| Tier | Meaning | Action Required |
|------|---------|-----------------|
| T1 | Confirmed bug | Must fix before merge |
| T2 | Likely bug | Should fix before merge |
| T3 | Design concern | Consider addressing |
| T4 | Suggestion | Optional improvement |

## Architecture

### Phase Pipeline

1. **Setup**: Identify changed Java files, create work directories
2. **Build Index**: Create enumerated file/line mapping for hallucination prevention
3. **Discovery**: Haiku agents scan code in parallel batches
4. **Quote Verification**: Verify each finding's code quote exists at claimed location
5. **Re-localization**: Sonnet agents independently locate each issue
6. **Build Context**: Extract code snippets around verified findings
7. **Verification**: Sonnet confirms/rejects findings with full context
8. **Opus Review**: Optional second opinion on high-severity (T1/T2) findings
9. **Generate Report**: Produce markdown report and task list

### Model Selection Rationale

- **Discovery (Haiku)**: Fast, cheap, good at pattern matching. Generates candidates.
- **Re-localization (Sonnet)**: Different model to avoid shared hallucination bias.
- **Verification (Sonnet)**: Needs reasoning to confirm/reject with evidence.
- **Opus Review**: Highest capability for final T1/T2 validation.

## Testing

Run unit tests for the hallucination defense library:

```bash
# Requires bash 4.2+
/opt/homebrew/bin/bash .claude/scripts/lib/test_hallucination_defense.sh
```

## Cost Control

The orchestrator tracks API costs and aborts if budget is exceeded:

- Default per-call limit: $0.50
- Default total budget: $5.00
- Configure via `max_total_budget_usd` in config

Cost is reported in the summary and embedded in the markdown report.

## CI Integration Example

```yaml
# GitHub Actions example
code-review:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
      with:
        fetch-depth: 0  # Need full history for diff

    - name: Install dependencies
      run: |
        sudo apt-get update
        sudo apt-get install -y jq bc

    - name: Run code review
      env:
        ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
      run: |
        ./.claude/scripts/code_review_orchestrator_v7.sh main --json-log

    - name: Upload review
      uses: actions/upload-artifact@v4
      with:
        name: code-review
        path: spec/code_review.md
```

## Troubleshooting

### "bash 4.2+ required"

Install newer bash: `brew install bash`, then run with `/opt/homebrew/bin/bash`.

### "bc is required"

Install bc (usually pre-installed on Linux; macOS: `brew install bc` or use system bc).

### "flock not found"

File locking is optional. Install with `brew install util-linux` for concurrent safety.

### High false positive rate

1. Enable all defense layers (don't use `--no-reloc` or `--no-quote-verify`)
2. Increase `reloc_tolerance` if legitimate findings are being rejected
3. Check the hallucination stats in the summary output

### Budget exceeded

Reduce scope by:
- Reviewing fewer files (smaller PR)
- Lowering `max_findings_to_verify`
- Disabling Opus review (`enable_opus_review: false`)
