# IYUM web

Active integrated IYUM application: Next.js App Router, React 19, Tailwind CSS 4. The earlier Figma/Vite export is historical; do not restore the SPA entrypoint or add a catch-all rewrite.

## Development

- Work in `/Users/tackeunoh/Developer/iyum/web`; check port 8443 before starting a server.
- Use pnpm 10.34.3 (packageManager and pnpm-lock.yaml). `.mise.toml` selects Node 22; package engines also allow Node 24. The migration was tested on Node 24.19.0.
- Install: `pnpm install --frozen-lockfile`.
- Develop: `pnpm dev`; production: `pnpm build` then `pnpm start`.
- Checks: `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build`.
- With production server running: `pnpm test:smoke` (BASE_URL defaults to http://127.0.0.1:8443).
- Read README.md for Vercel upload/root conventions. Do not deploy or change dashboard settings without a request.

## Structure

- `src/app/`: server layouts, individual route entrypoints, metadata, robots and not-found. No `output: export` or SPA catch-all.
- `src/features/`: existing screens/content moved from `src/pages/` without redesign. Do not create a top-level `src/pages/` for feature files: Next would interpret it as Pages Router.
- `src/components/Shell.tsx`: server header/footer; GlobalNav and HomeSearch are interactive client components.
- `src/components/AppLink.tsx`, `NavigationGuard.tsx`: Next Link and existing unsaved-post confirmation. Preserve browser history state owned by Next.
- `src/lib/useQueryParams.ts`: query-driven filters; `mock-posts.tsx`: historical/local example data, not the active community API.
- `src/features/HearingAidGuide.tsx`: P03 questions/results/navigation/copy, browser interactions in handlers/effects. Reference and guardian views render into initial HTML and are hidden until selected. Keep hidden sections out of visible focus order.
- `src/features/HearingAidBenefits.tsx`, `RegistryCheck.tsx`, `DirectClaimGuide.tsx`: shared P03 benefits, product/store confirmation and direct-claim explanation.
- `src/data/hearingAidProducts.json`: existing 206-product snapshot; retain source/freshness notices.
- `src/index.css`: Tailwind import, external font imports and existing global styles. Root layout imports this file. Tailwind 4 uses `@tailwindcss/postcss` in `postcss.config.mjs`.
- `src/features/HearingAidGuide.css`: existing P03 styles; preserve them for unrelated changes.
- `src/imports/`, `.figma/`, `dist/`: historical export/tooling/generated material; not current application entrypoints or instructions.
- App records belong in `docs/`. Do not rewrite historical validation claims; append dated results.

## Preservation and boundaries

- Keep current design, policy wording/amounts, sources, unknown-answer handling and copied text consistent.
- Use Next navigation, retain public routes and query/hash behavior. Public content must be in initial HTML; do not wrap the entire app with browser-only dynamic rendering.
- Client Components still render on the server. Access browser APIs in events/effects or SSR-safe external-store snapshots.
- P03 currently includes medical-aid history Q4 and application Q5. Preserve the actual code, not the older 15-result handoff alone.
- P03 answers, selected products and the benefit view are local state; do not transmit or persist answers as part of unrelated work.
- Keep medical-aid/insurance-unknown amount visibility rules, history yes/unknown distinction and conditional existing-device wording.
- Authentication and community post creation/list/detail use the Spring Boot API. Community comments now use the Spring API for public cursor reads and authenticated create/update/delete; likes and best-post aggregation remain unimplemented; home example data is labelled separately. Preserve existing API/session/CSRF contracts; do not add backend logic implicitly to Next route handlers.
- Do not print `.env` values or Vercel credentials. Only explicitly public browser configuration may use NEXT_PUBLIC_.
- Do not commit, push or deploy unless requested.

<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->
