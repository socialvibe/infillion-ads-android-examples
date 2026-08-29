# AGENTS.md

Behavioral and workflow guidelines for AI coding agents (and humans) working in this repo. Product and run information lives in [README.md](README.md); build, test, and release instructions live in [CONTRIBUTING.md](CONTRIBUTING.md). This file is about *how we work*, so they don't overlap.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 0. MCP Tools

- **Jira:** Atlassian MCP — project `PI` ("PRGM - Publisher Integration"). Read, create, and transition tickets (see §5, §7).
- **GitHub:** use the `gh` CLI for PRs and issues.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

## 5. Session Start

At the start of every session, in order:

1. Read [README.md](README.md) and [CONTRIBUTING.md](CONTRIBUTING.md) for project facts, and `CLAUDE-LOCAL.md` if present (optional, git-ignored personal notes). Incorporate their key data into your working context.
2. Check the current branch (`git branch --show-current`):
   - **If on the default branch (`main`) and `origin` exists**:
     1. Fast-forward local `main` to `origin/main` (`git fetch origin && git pull --ff-only`). Never branch from a stale `main`.
     2. Ask for the ticket number using `AskUserQuestion` with "Type Ticket Number" as the first option (follow up asking the user to type it if selected) and "Use NONE-001" as the second.
     3. Ask whether this is feature or bug work and for a short branch description. Create and switch to `feature/<TICKET>/<description>` or `bugfix/<TICKET>/<description>` before changing code.
   - **If on local `main` and no remote exists**: the repository is still being bootstrapped. Direct work on `main` is allowed until the initial remote push.
   - **If on a feature or bugfix branch**: derive the ticket from the branch name (e.g. `feature/PI-3333/fix-something` → `PI-3333`, other possible prefixes: `ADX-XXXX`). Confirm it with the user in a single line of text.
3. Store the ticket for the rest of the session — reuse it for branch naming, commit messages, and PR titles without asking again.
4. **Commit messages** use `<TICKET> - <MESSAGE>` (e.g. `PI-3670 - Restructure ad tags catalog`). See [CONTRIBUTING.md](CONTRIBUTING.md) § Commits.

## 6. Versioning

**Every PR bumps the Android app version exactly once.**

Never commit directly to remote `main`; company branch protection requires an approved PR and a manual merge.

Version values live in [gradle.properties](gradle.properties):

- Increment `VERSION_CODE` by exactly one.
- Increment the patch component of `VERSION_NAME` by exactly one (for example, `1.2.3` → `1.2.4`).
- Do not change the major or minor component in a normal PR.

The pull-request workflow fails if either increment is missing or incorrect. After an approved PR is manually merged to `main`, the release workflow creates tag `v<VERSION_NAME>` and the matching GitHub release.

## 7. Pull Requests

If directly opening PRs on GitHub you **MUST**:

1. Use the ticket number and short change description as the pull request title in the same form as commits: `PI-3670 - <short description>`. **PR title must be under 80 characters** (company policy).
2. Use the pull request template at `.github/pull_request_template.md` as the PR description and fill it with data from the change.
3. After the PR is open, offer to open it in the browser using `gh pr view --web`.
4. **Never offer to merge a PR.** PRs must be approved under company policy and manually merged through the GitHub web interface.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.