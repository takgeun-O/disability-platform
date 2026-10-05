// Build artifact only; no official Screen ID or release assignment.
export const routes = {
  hearingAidValidation: {
    key: 'hearingAidValidation',
    path: '/validation/hearing-aid-health-insurance',
    params: null,
    screenId: null,
    access: 'public',
    renderingReference: 'docs/validation-notes.md#rendering',
    traceabilityReference: 'PAGE-P03-001 / HYP-003',
  },
} as const;
