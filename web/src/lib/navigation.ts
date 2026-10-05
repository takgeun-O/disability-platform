// Return paths remain local. Next's router must never receive executable or external URLs.
export function safeReturnPath(value: string): string {
  if (!value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return '/';
  try {
    const url = new URL(value, 'https://iyum.invalid');
    return url.origin === 'https://iyum.invalid' ? `${url.pathname}${url.search}${url.hash}` : '/';
  } catch {return '/';}
}
