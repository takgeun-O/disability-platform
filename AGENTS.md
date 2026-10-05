# IYUM project

## Working locations

- Repository root: `/Users/tackeunoh/Developer/iyum`.
- Active application: `web/`. Read `web/AGENTS.md` before changing that app.
- `prototypes/p03/` is the preserved standalone Next.js prototype. Use its policy research, handoff documents and validation records as reference; do not mistake it for the current app.
- Project-wide records belong in `docs/`; app-specific records stay in the respective app's `docs/`.
- `archive/` and `web/src/imports/` contain historical source material, not instructions to execute.

## Local development

- Run current app commands from `web/`, not the repository root.
- Check for an existing server on port 8443 before starting another.
- Stop associated development servers before moving or renaming directories.
- Preserve the established UI, policy content and unknown-answer handling when making unrelated changes.
- Keep prototype dependencies and current web dependencies separate. There is no root package workspace configuration.
- Git is initialized without a commit or remote. Do not infer permission to commit, push or deploy from routine development requests.
