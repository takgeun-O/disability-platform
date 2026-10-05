Extend the **existing desktop low-fidelity prototype** by creating a new **Community Post Detail** screen.

This screen is part of the same Korean accessibility-focused community + information platform as the existing:

* Home
* Community Home
* Post List

The new screen must feel like a natural continuation of the existing prototype.

All user-facing UI text and realistic sample content must remain in **Korean**.

---

# 1. Critical Prototype Preservation Requirements

Preserve the existing prototype architecture.

Do NOT:

* redesign the existing Home
* redesign the existing Community Home
* redesign the existing Post List
* rebuild the shared Header
* rebuild the shared Global Navigation
* rebuild the shared Footer
* introduce a new visual language
* create a separate Community microsite

Create Post Detail as a **separate new screen/route**.

The existing Home, Community Home, and Post List should remain structurally unchanged.

One exception is required:

## Question-status terminology consistency

The existing Post List contains legacy question-status copy such as:

**답변 완료**

Replace legacy Community question-status labels with the official MVP labels where they appear.

Official user-facing question states are:

* `OPEN` → **답변 대기**
* `ANSWERED` → **답변 있음**
* `RESOLVED` → **해결됨**

Do NOT use:

* 답변 완료
* CLOSED
* accepted-answer UI

Do not otherwise redesign the Post List.

---

# 2. Screen Role

Create the **Post Detail** screen.

Its purpose is to allow users to:

* read one Community post
* understand its Post Type and Primary Topic
* read comments and one-level replies
* perform permitted actions such as recommendation, save, share, and report
* edit or delete the post if they are the author
* return to the previous browsing context

The Post Detail is not:

* a Post List
* a Post Create form
* a social-media feed
* a recommendation page
* a dashboard

---

# 3. Default State for This Iteration

Use a **GENERAL post** as the primary default Post Detail state.

Use:

* Post Type: `일반`
* Primary Topic: `자유·일상`
* Normal published state
* logged-in regular member
* viewer is NOT the post author
* recommendation state: not recommended
* save state: not saved
* 2–3 comments
* one example one-level reply
* no attached image in the primary default frame

This default state should validate the common structural layout without mixing in question-specific behavior.

Do not combine:

* Loading
* Error
* Report Dialog
* deletion state
* question state
* image-attachment state

into the primary default screen.

---

# 4. Shared Header

Reuse the existing shared Header exactly in the same structural style as the existing prototype.

Preserve:

* Logo
* Global Search
* authentication/account area

The Header Global Search remains the service-wide search.

Do not add a Post-specific search field.

---

# 5. Global Navigation

Reuse the existing Global Navigation.

Keep:

* 홈
* 커뮤니티
* 복지·지원정보
* 병원·전문기관
* 보조기기

Indicate `커뮤니티` as the current service area.

The current state must not rely on color alone.

Keep navigation behavior:

* Logo → Home
* `홈` → Home
* `커뮤니티` → Community Home

Do not create Post Detail-specific global navigation.

---

# 6. Post Context

Near the title, display the Post classification in a restrained metadata hierarchy.

For the default GENERAL post:

**일반 · 자유·일상**

For a QUESTION variant, the architecture should support:

**질문·답변 · 인공와우 · 답변 대기**

or:

**질문·답변 · 인공와우 · 답변 있음**

or:

**질문·답변 · 인공와우 · 해결됨**

Question status appears only for QUESTION posts.

Do not display:

* Board
* Category
* Tag
* Disability Type

Do not communicate question status through color alone.

---

# 7. Post Header

Use the post title as the page H1.

Required hierarchy:

**Post Type · Primary Topic**

# 게시글 제목

작성자 · 작성일

Optional/conditional:

* `수정됨`
* question status for QUESTION posts
* Community-wide notice label if this is a notice variant

For the primary default screen, keep metadata restrained.

Do NOT display by default:

* 조회 수
* 추천 수
* 댓글 수 in the Post Header
* author activity score
* large profile statistics

The title and body should dominate the screen.

---

# 8. Author Information

Keep author information minimal.

Use:

* 작성자 닉네임
* optional lightweight avatar only if consistent with the existing prototype

Do not create a large author profile card.

Do not expose:

* disability type
* medical information
* workplace
* location
* follower counts
* activity scores
* registration date

Do not make the author name a profile link unless the existing product architecture already requires it.

---

# 9. Post Body

The post body is the primary reading content.

Use a simple text-first structure with:

* paragraphs
* line breaks
* realistic Korean sample text

Keep the body width comfortable for long-form reading.

Do not introduce unsupported rich content such as:

* code blocks
* embedded video
* audio
* complex tables
* advanced quote components
* rich editor-specific visual elements

Do not let metadata or actions visually overpower the body.

---

# 10. Image Attachment Architecture

The MVP may support attached images.

Do not include an image in the primary default frame.

However, make the structure capable of supporting an image-attachment variant later.

An attached image should appear naturally within the article flow.

Do not add:

* PDF attachments
* document-file download UI
* video attachments
* audio attachments

---

# 11. Post Actions

After the main body, provide a restrained Post Action group.

MVP actions:

* 추천
* 저장
* More

Optional:

* 공유

Use clear Korean text labels.

Do not create a social-media reaction bar.

Do not visually overload this area.

For the default logged-in non-author state:

* `추천`
* `저장`
* `공유`
* More

are appropriate.

The actions should remain visually secondary to the article content.

---

# 12. Recommendation

Use the official UI terminology:

**추천**

Toggle states:

* 추천
* 추천됨

Do NOT use:

* 좋아요
* Like

Do not display recommendation count in the primary Low-Fidelity state.

Do not expose the list of users who recommended the post.

For logged-out users, selecting Recommendation should conceptually trigger Login and then return to the same Post Detail.

Do not automatically execute the recommendation after login unless explicitly required later.

---

# 13. Save

Use the user-facing terminology:

* 저장
* 저장됨

The internal domain concept may be Bookmark, but the UI must use `저장`.

Do not expose save state publicly to other users.

For logged-out users:

**저장 → 로그인 → same Post Detail**

Do not add folders or notes.

---

# 14. Share

For Desktop Low-Fidelity, if Share is included, support only a simple:

**공유 → 링크 복사**

Do not add:

* KakaoTalk buttons
* Facebook
* X/Twitter
* Instagram
* other SNS service buttons

Share does not require login.

Keep completion feedback structurally possible, for example a Toast later.

---

# 15. More Menu — Non-Author

For a logged-in viewer who is NOT the author:

More Menu should provide:

* 신고

Do not expose author actions.

Do not place Report as a large primary button.

---

# 16. More Menu — Author Variant

The architecture should support an author-owned Post variant.

For the author, More Menu contains:

* 수정
* 삭제

Do not show `신고` for the user's own post.

Do not create the author variant as the primary default frame unless useful as a small state reference.

Conceptual flows:

**수정 → Post Edit → original Post Detail**

**삭제 → Confirmation Dialog → Post List**

Do not create Post Edit in this iteration.

---

# 17. Report

Report is an MVP operational function.

For Desktop:

**신고 → Dialog**

Do not place the full report form permanently in the Post Detail page.

The Dialog may later support:

* 신고 사유
* optional explanation
* 제출

Do not create the full Report Dialog in the primary default frame unless needed for prototype interaction.

Report requires login.

Reporting does not immediately hide or delete the original content.

---

# 18. Question Status Contract

Use the following official MVP contract.

## OPEN

UI:

**답변 대기**

Meaning:

No valid answer/comment exists yet.

## ANSWERED

UI:

**답변 있음**

Meaning:

At least one valid answer/comment exists, but the author has not marked the question as resolved.

## RESOLVED

UI:

**해결됨**

Meaning:

The question author explicitly marked the question as resolved.

Rules:

* `ANSWERED` and `RESOLVED` are different states.
* Do not use `답변 완료`.
* Do not expose `CLOSED` as a normal MVP user-facing state.
* Do not create accepted-answer functionality.
* Do not create an accepted-answer badge.
* Do not create a “best answer” system.

A separate QUESTION variant may be created later using these labels.

For this iteration, the default Post Detail remains a GENERAL post.

---

# 19. Question Author Action

The structure should later support the question author explicitly changing resolved state.

Do not make this action prominent in the default GENERAL post.

For a future QUESTION author variant:

* unresolved question → allow `해결됨으로 표시`
* resolved question → allow `해결 해제`

Do not implement answer selection.

Do not invent additional states.

---

# 20. Comment Section

After the Post Action group, create a clear Comment Section.

The Comment Section should include:

* section heading
* Comment Form
* Comment List

Use a simple vertical structure.

Do not make the comments look like a separate social feed.

---

# 21. Comment Form

For the default logged-in state, provide an inline Comment Form.

Structure:

**댓글 작성**

[ Textarea ]

[ 등록 ]

Keep the form simple.

Do not add:

* rich text toolbar
* attachment controls
* emoji picker
* GIF picker
* media upload

Empty comments cannot be submitted conceptually.

Do not finalize exact character limits.

For a logged-out variant, the form should communicate that login is required rather than showing a fully active form.

---

# 22. Comment Item

Each Comment should prioritize:

* 작성자
* 내용
* 작성일

Optional/conditional:

* 수정됨
* More Menu
* 답글 action

Do not show Comment recommendation because it is not part of the current MVP specification.

Avoid excessive metadata.

Example conceptual structure:

작성자 · 작성일

댓글 내용이 표시됩니다.

답글　　　　　　More

---

# 23. Reply Structure

Replies are supported.

Maximum visual depth:

**1 level**

Example:

Comment
└── Reply
└── additional replies remain at the same Reply level

Do not create infinitely nested threads.

Use more than indentation alone to communicate the semantic relationship.

Possible lightweight structural cues:

* indentation
* divider
* reply grouping

Do not add mention functionality unless already required.

---

# 24. Comment / Reply Actions

For the author's own Comment or Reply:

More Menu:

* 수정
* 삭제

For another user's Comment or Reply:

* 답글
* More → 신고

Do not display all actions as a reaction bar.

Do not add Comment Like/Recommendation.

---

# 25. Deleted Comment Variant

The architecture should support deleted comments.

If a deleted comment has no replies, it may disappear.

If replies remain, the original comment may display:

**삭제된 댓글입니다.**

while preserving its replies.

Do not show backend soft-delete terminology to users.

---

# 26. Post List Return Context

Preserve the user's previous browsing state conceptually.

If the user entered from Post List, Browser Back should restore:

* Community search keyword
* Post Type filter
* Topic filter
* Sort
* Page
* scroll position where practical

Do not force the user back to:

* Community Home
* Post List page 1
* Home

after reading a post.

A dedicated `목록으로` action is not required in the default screen unless already present in the existing prototype convention.

Deleted/unavailable states may include explicit recovery actions.

---

# 27. Other Entry Contexts

The same Post Detail must support entry from:

* Community Home
* Home Community Preview
* Global Search
* notification
* saved-post list
* shared URL
* direct URL

Do not create different Post Detail versions for each source.

Browser Back should respect the actual entry context.

---

# 28. Related Content Exclusion

Do NOT add:

* 관련 게시글
* 같은 주제 게시글
* 인기 게시글
* 추천 게시글
* personalized recommendations
* AI recommendations

These are not part of the current MVP Post Detail.

---

# 29. Previous / Next Post Exclusion

Do NOT add:

* 이전글
* 다음글

They are not part of the current MVP Post Detail design.

---

# 30. Logged-Out Behavior

Public Post Detail remains viewable without login.

Logged-out users may:

* read the post
* read comments/replies
* copy/share the public URL

Logged-out users must authenticate when attempting:

* 추천
* 저장
* 댓글 작성
* 답글 작성
* 신고

After login, return to the same Post Detail context.

Do not send users to Home after authentication.

---

# 31. Loading Architecture

Do not create a full Loading screen in the primary frame.

The architecture should support:

* Post Header Skeleton
* Post Body Skeleton
* Comment Section Loading

Keep shared Header and Global Navigation available where appropriate.

If only comments are loading, keep the post content visible.

Do not turn the entire screen into Skeleton for a partial request.

---

# 32. Error Architecture

The structure should later support:

### Post fetch failure

* error message
* retry
* previous/list recovery

### Post not found

* unavailable message
* navigation recovery

### Deleted Post

**삭제된 게시글입니다.**

[목록으로]

### Permission denied

* clear access message

### Comment fetch failure

* Post content remains visible
* Comment Section shows error
* `다시 시도`

Do not display these states in the primary default frame.

---

# 33. Accessibility

Accessibility is a core requirement.

Support:

* Post title as the single H1
* meaningful article structure
* logical section headings
* readable author/date metadata
* explicit Action labels
* keyboard-accessible Recommendation / Save / Share / More
* visible focus states
* meaningful toggle state labels:

  * 추천 / 추천됨
  * 저장 / 저장됨
* textual question-status labels
* clear Comment Form label
* accessible validation errors
* semantic Comment/Reply hierarchy
* keyboard-accessible Dialogs
* correct focus movement into and out of Dialogs
* accessible dynamic feedback
* text zoom/reflow
* long-content readability
* no horizontal scrolling caused by article text

Do not communicate important states through color alone.

---

# 34. Desktop Layout

This iteration is **Desktop-first**.

Use a main single-column reading structure.

Prefer:

* shared Header
* shared GNB
* readable article container
* Post Context
* Title
* author/date metadata
* body
* Post Action group
* Comment Form
* Comment List
* Footer

Do NOT add a Sidebar.

Do not fill empty desktop space with extra widgets.

---

# 35. Future Responsive Behavior

Do not create full Tablet or Mobile screens in this iteration.

However, ensure the structure can later adapt.

On smaller screens:

* metadata may wrap
* Post Actions may wrap or move secondary actions into More
* images should be responsive
* Comment Form becomes full-width
* one-level Reply structure remains
* Report Dialog may become Bottom Sheet or Full Screen
* author actions remain in More

Do not simply scale down the Desktop screen proportionally.

---

# 36. Low-Fidelity Scope

This is still a **Low-Fidelity structural prototype**.

Do not finalize:

* brand colors
* final typography
* exact grid
* exact spacing tokens
* exact pixel dimensions
* border radius system
* shadows
* gradients
* illustrations
* final icons
* animation
* visual branding

Use the same restrained Low-Fidelity language as the existing prototype.

Focus on:

* reading hierarchy
* interaction hierarchy
* permissions
* Post Actions
* Comment/Reply relationships
* navigation continuity
* accessibility

---

# 37. Explicit Exclusions

Do NOT add:

* Board UI
* Community Category
* Tag UI
* Disability Type
* Community Space
* Sidebar
* Related Posts
* Popular Posts
* Recommended Posts
* Trending
* personalized feed
* AI summary
* AI recommendations
* ads
* author profile card
* follower system
* social-media reaction bar
* accepted answer
* best-answer badge
* Comment likes
* infinite nested replies
* Previous Post / Next Post
* unsupported SNS share buttons
* Post Create form
* unsupported rich-text elements
* Phase 2+ feature placeholders

---

# 38. Prototype Navigation Requirements

Extend the existing prototype rather than creating an isolated screen.

Preserve existing:

**Home**

**Community Home**

**Post List**

Add:

**Post Detail**

Connect:

### Post List

Post Title → Post Detail

### Community Home

Recent Post Preview → corresponding Post Detail

### Home

Community Preview → corresponding Post Detail where applicable

### Post Detail

* Logo → Home
* GNB `홈` → Home
* GNB `커뮤니티` → Community Home
* Browser Back → previous entry context
* `수정` → future Post Edit
* `신고` → future Report Dialog/Flow
* delete success → Post List
* saved-post entry → same Post Detail

Do not create full Post Edit, Login, Report, or other new screens in this iteration.

---

# Final Goal

Create a **Desktop Low-Fidelity Community Post Detail** that feels like the next natural step after the existing Post List.

The screen should answer:

**“What is this post about, what does the author say, how can I participate, and how do I return to where I came from?”**

Prioritize:

**Post Type / Topic context
→ title
→ author/date
→ readable body
→ restrained Post Actions
→ Comment Form
→ Comments / one-level Replies
→ context-preserving navigation**

Preserve the existing Home, Community Home, and Post List.

Create only the Post Detail in this iteration, except for correcting legacy Community question-status labels in the existing Post List from `답변 완료` to the official status labels where appropriate.
