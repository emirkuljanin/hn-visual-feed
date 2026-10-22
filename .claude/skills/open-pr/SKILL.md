---
name: open-pr
description: Open or update a pull request (a Gitblit ticket) for the HN Visual Feed project on the user's local Gitblit server. Use when a planned task is finished and verified, or when review feedback needs a new patchset.
---

# Open a PR on Gitblit

Remote: `origin` = `http://admin@192.168.1.219:8080/r/hn-visual-feed.git`, base branch `master`.
Gitblit "tickets" are its pull requests; they are created by pushing to magic refs.

Never type or store the Gitblit password. Git Credential Manager prompts the user and caches it.

## Workflow (one PR per planned task)

1. Start from an up-to-date base: `git checkout master && git pull --ff-only`.
2. Branch: `git checkout -b <type>/<short-name>` (`feature/`, `fix/`, `chore/`, `test/`, `docs/`).
3. Do the work, then run the `verify-build` skill. Do not open a PR with failing or unrun checks without
   saying so in the description.
4. Commit, then run the `pr-reviewer` agent (`.claude/agents/pr-reviewer.md`) on the branch. Fix its verified
   findings (re-run `verify-build` if code changed) or note in the description why one was left. Add a
   "Pre-review" section to the description listing what it found and what changed.
5. Commit as the user (repo-local git config: Emir Kuljanin <emirkuljanin@gmail.com>). Write the commit message
   using the template below. Gitblit uses a single commit's subject as the ticket title and its body as the
   ticket description, so squash the branch into one commit before the ticket is opened (rewriting is fine
   until then; use `git push --force-with-lease` for the reference branch).
6. Push the branch for reference and create the ticket:
   ```bash
   git push -u origin HEAD
   git push origin HEAD:refs/for/master
   ```
   The push output prints the new ticket number and URL. Report both to the user.
7. Stop. Wait for the user to say the ticket is approved/merged before starting the next task.

## Updating a PR after review

Add new commits (don't rewrite pushed history) and push a new patchset to the same ticket:
`git push origin HEAD:refs/for/<ticket-number>` (and `git push origin HEAD` for the branch).

## Description template (commit body)

```
<Short imperative title, <= 72 chars>

## What
- Bullet list of the changes, grouped by module.

## Why / decisions
- The choices a reviewer might question, with the alternative considered and why it lost.

## How it was tested
- Exact checks run (from verify-build) and their result; anything skipped and why.

## Pre-review
- What the pr-reviewer agent found (severity + one line each) and what was changed, or why it was left.

## Follow-ups
- Deliberately deferred items, linked to the PR plan.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```
