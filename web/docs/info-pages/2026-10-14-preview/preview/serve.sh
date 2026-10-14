#!/bin/sh
# Preview only. Does not start or modify the Next/Spring service.
cd "$(dirname "$0")" || exit 1
exec python3 -m http.server "${IYUM_PREVIEW_PORT:-8450}" --bind 127.0.0.1
