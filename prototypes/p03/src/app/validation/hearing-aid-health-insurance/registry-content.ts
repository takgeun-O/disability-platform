// Public official notice snapshot for usability testing. No runtime NHIS requests.
import catalog from './data/hearing-aid-products.json';
export const registrySource = catalog.sourceUrl;
export const registryCheckedAt = catalog.checkedAt;
export const registryEffectiveAt = catalog.effectiveAt;
export const freshnessDays = 7; // Prototype branch only; not an operational SLA.
export type RegistryProduct = { id: string; model: string; company: string; price: number; sourceUrl: string; checkedAt: string };
export const registryProducts: readonly RegistryProduct[] = catalog.products.map(product => ({ ...product, sourceUrl: registrySource, checkedAt: registryCheckedAt }));
export type RegistryStatus = 'idle' | 'verified' | 'not-found' | 'ambiguous' | 'stale' | 'source-unavailable';
export type RegistryResult = { status: RegistryStatus; product?: RegistryProduct; simulated: boolean };
export type RegistryContext = { product: RegistryResult };
export const emptyRegistry: RegistryContext = { product: { status: 'idle', simulated: false } };
export const registryStatusText: Record<RegistryStatus, string> = {
  idle: '아직 확인하지 않았어요.',
  verified: '공식 등록자료에서 확인됐어요.',
  'not-found': '이 공식 고시 목록에서 찾지 못했어요.',
  ambiguous: '정확한 제품을 선택해 주세요.',
  stale: '공식 데이터 최신 확인이 지연되고 있어요.',
  'source-unavailable': '현재 공식 등록정보를 확인할 수 없어요.',
};
export function normalizeRegistryQuery(query: string) {
  // Ignore typing variations, never remove model suffix letters/digits.
  return query.normalize('NFKC').toLocaleLowerCase('en').replace(/[\s‐‑–—-]+/g, '');
}
const aliases: Record<string, string[]> = {
  '스타키': ['starkey', '스타키'], '포낙': ['phonak'],
};
export function matchProducts(query: string) {
  const value = normalizeRegistryQuery(query);
  if (!value) return [];
  const exact = registryProducts.filter(p => [p.model, p.id].some(field => normalizeRegistryQuery(field) === value));
  if (exact.length) return exact;
  const terms = aliases[value] ?? [value];
  return registryProducts.filter(p => terms.some(term => [p.model, p.id, p.company].some(field => normalizeRegistryQuery(field).includes(term))));
}
export function isRegistryStale(checkedAt: string, now: number) {
  const checked = Date.parse(`${checkedAt}T00:00:00+09:00`);
  return !Number.isFinite(checked) || now < checked || now >= checked + freshnessDays * 86_400_000;
}
