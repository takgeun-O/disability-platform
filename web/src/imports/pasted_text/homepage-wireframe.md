Create a **desktop-first low-fidelity homepage wireframe** for a Korean accessibility-focused community and information platform.

The platform primarily serves people with hearing disabilities during the MVP phase, but the overall UI architecture must remain disability-neutral because the service will later expand to support other disability groups.

**Important: All user-facing UI text and sample content must be written in Korean.**

Do not create a polished high-fidelity visual design yet. The purpose of this iteration is to validate **information architecture, content hierarchy, navigation, user flows, CTA placement, and responsive structure**.

---

## 1. Product Character

This product combines two characteristics:

* A community where users can share real experiences and practical knowledge.
* A trustworthy information platform for discovering welfare programs, professional institutions, hospitals/hearing centers, and assistive devices.

The visual structure should balance these two characteristics.

Avoid making the service look:

* overly clinical like a hospital website,
* overly governmental or bureaucratic,
* overly casual like a social network,
* or overly commercial like an e-commerce website.

Prioritize clarity, trust, accessibility, and easy information discovery.

---

## 2. Homepage Purpose

The homepage is a **discovery hub**, not a page where users consume all available information.

The primary user journey should be:

**Direct information search
→ Entry into core services
→ Discovery of useful content
→ Navigation to list or detail pages**

Users must be able to browse public information without being required to sign in or create an account.

---

## 3. Desktop Homepage Structure

Create the page in the following information priority.

### A. Header

Include:

* Logo
* Search access
* Login
* Sign Up

Use the logged-out state for this first wireframe.

The architecture may later support notification and profile controls for authenticated users, but they do not need to appear in this version.

Keep the header simple and functional.

---

### B. Global Navigation

Include these navigation items:

* 홈
* 커뮤니티
* 복지·지원정보
* 병원·전문기관
* 보조기기

Clearly indicate that **홈** is the current page.

Do not add AI navigation in the MVP version.

Navigation labels and placement should remain consistent and easy to scan.

---

### C. Hero / Primary Discovery Area

This is the most important area of the homepage.

Include:

* A concise service message
* A prominent global search field

Suggested Korean headline:

**필요한 장애 관련 정보를 찾아보세요**

Suggested search guidance:

**복지, 병원, 보조기기, 커뮤니티 검색**

Search should be the primary action in the hero.

The hero should support actual information discovery rather than marketing.

Do NOT use:

* large promotional imagery,
* advertising banners,
* video backgrounds,
* auto-playing media,
* carousels,
* or decorative illustrations that dominate the content.

Keep this area functional, clear, and relatively compact.

---

### D. Core Service Shortcuts

Provide clear entry points to these four services:

1. 커뮤니티
2. 복지·지원정보
3. 병원·전문기관
4. 보조기기

Each entry should include a visible text label.

Icons may be used only as supporting visual elements.

A short description may be included to explain what users can find in each section.

Do not make these shortcuts overly decorative or visually heavy.

---

### E. Community Preview

Show a simple preview of recent community posts.

Possible information:

* 게시글 제목
* 게시판 또는 카테고리
* 작성일
* 댓글 수

Provide a clear action:

**커뮤니티 더보기**

Keep the structure simple.

Do not introduce complex tabs such as Popular / Latest / Questions during the initial MVP.

Selecting a post should conceptually lead directly to the original post detail page.

---

### F. Welfare & Support Information Preview

Show a small number of welfare or support programs.

Possible information:

* 사업명
* 지원 대상
* 신청 기간
* 현재 상태

Provide:

**지원정보 더보기**

Status must not be communicated through color alone. Include a textual status label where appropriate.

This section should feel like trustworthy structured information and be visually distinguishable from user-generated community content.

---

### G. Hospitals & Professional Institutions

Create a concise discovery section for hospitals and professional institutions.

Primary CTA:

**기관 찾기**

A small number of institution previews may be shown if useful, but avoid overloading the homepage.

Do NOT create:

* 내 주변 병원
* 가까운 기관
* map-based discovery
* location-aware recommendations

because location functionality is not part of the current MVP.

---

### H. Assistive Devices

Create an entry point for assistive-device discovery.

Primary CTA:

**제품 찾기**

If product previews are included, limit the information to:

* 제품명
* 제조사
* 제품 유형
* one representative piece of information

Do not provide detailed specifications or product comparison functionality on the homepage.

Avoid making this section resemble an online store.

---

### I. Footer

Include a clean, secondary footer containing items such as:

* 서비스 소개
* 이용약관
* 개인정보처리방침
* 운영정책
* 문의
* 접근성 안내
* 운영 주체 정보

The footer must not function as a primary navigation system.

---

## 4. UX Principles

Follow these principles throughout the page:

* Prioritize clarity over decoration.
* Organize information around user goals rather than internal system structure.
* Present the most important information and actions first.
* Avoid multiple competing primary actions within the same section.
* Use consistent terminology and interaction patterns.
* Keep homepage previews concise and move detailed information to dedicated list/detail pages.
* Clearly distinguish official information from user-generated experiences.
* Do not require authentication to browse public information.
* Avoid unnecessary cognitive load.
* Do not fill empty space simply because desktop screens are large.
* Do not introduce functionality that does not exist in the MVP.
* The homepage should remain usable even if one preview section temporarily fails to load.

---

## 5. Accessibility Principles

Accessibility is a core product requirement, not a decorative feature.

Design the structure with the following principles in mind:

* Strong text readability
* Clear visual hierarchy
* Sufficient visual contrast
* Do not communicate state or selection using color alone
* Avoid excessive icon-only controls
* Provide visible text labels for important actions
* Allow sufficiently large interactive areas
* Avoid rigid text containers that could break when text is enlarged
* Use a logical heading hierarchy
* Arrange content in a logical keyboard navigation order
* Account for visible focus states
* Avoid unnecessary motion

Accessibility should not make the product visually resemble a hospital or government website.

---

## 6. Responsive Architecture

Design the desktop structure so it can later adapt naturally to tablet and mobile layouts.

### Desktop

* Use a reasonably wide search field.
* Multi-column layouts may be used where they improve scanning.
* Maintain comfortable content width and whitespace.
* Do not add unnecessary information simply to fill horizontal space.

### Tablet

The architecture should be capable of transitioning to:

* fewer columns,
* two-column or single-column layouts,
* and a more compact navigation system.

### Mobile

The structure should eventually collapse into a single-column layout in this order:

**Hero
→ Core Services
→ Community
→ Welfare & Support Information
→ Hospitals & Professional Institutions
→ Assistive Devices
→ Footer**

The mobile version may later use a compact menu or drawer for global navigation.

Do not remove access to any core service simply because the viewport is smaller.

Do not create the full mobile screen in this iteration unless necessary to establish responsive behavior.

---

## 7. Low-Fidelity Requirements

This is specifically a **low-fidelity wireframe iteration**.

Focus on:

* information hierarchy,
* layout,
* section relationships,
* navigation,
* CTA placement,
* content density,
* and future responsive behavior.

Do NOT spend significant effort on:

* final brand colors,
* polished illustrations,
* gradients,
* elaborate shadows,
* detailed animations,
* decorative effects,
* or a finalized design system.

Use restrained neutral styling.

Do not treat specific font sizes, spacing values, grid dimensions, border radii, or breakpoints as finalized design decisions.

Realistic Korean sample content may be used where it helps evaluate the layout.

---

## 8. Explicit Exclusions

Do NOT add any of the following:

* AI Gateway
* AI-focused hero
* AI assistant/chat interface
* personalized recommendations
* interest-based recommendations
* activity-based recommendations
* location-based recommendations
* nearby hospital functionality
* automatic carousels
* auto-playing video
* promotional hero banners
* dashboard-style information overload
* complex community tab navigation
* future-feature placeholders
* features that were not explicitly described above

Do not infer additional product functionality simply because it is common in similar platforms.

---

## 9. Design Restraint

Avoid excessive use of cards.

Not every section needs to be represented as a large rounded card.

Use a mixture of:

* simple lists,
* grouped information,
* lightweight containers,
* clear section headings,
* and cards only where they meaningfully improve comprehension.

Avoid excessive badges, pills, icons, shadows, and rounded containers.

The homepage should feel like a coherent information platform rather than a collection of independent UI cards.

---

## Final Objective

The first result should be a **desktop-first low-fidelity homepage prototype**, not a finished brand design.

The primary question this wireframe should help answer is:

**“Does this information structure make it easy for users to understand the service, find information, enter the core service areas, and discover useful content?”**

Prioritize structure and usability over visual polish.

Generate the homepage first. Do not expand the prototype into additional pages yet.
