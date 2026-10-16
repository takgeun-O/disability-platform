"use client"
// 작성과 수정이 같은 입력 정책·라벨·오류 연결을 사용한다.
import {
  POST_TYPES,
  POST_TOPICS,
  characterCount,
  normalizeTitle,
  type PostForm,
  type PostField,
  type PostFieldErrors,
} from "@/lib/community-posts"
export default function PostFields({
  values,
  errors,
  disabled,
  onChange: edit,
}: {
  values: PostForm
  errors: PostFieldErrors
  disabled: boolean
  onChange: (field: PostField, value: string) => void
}) {
  const field = (name: PostField) => ({
    id: `post-${name}`,
    name,
    "aria-invalid": !!errors[name],
    "aria-describedby": errors[name] ? `post-${name}-error` : undefined,
  })
  const errorText = (name: PostField) =>
    errors[name] && (
      <p className="iyum-field-error" id={`post-${name}-error`}>
        {errors[name]}
      </p>
    )
  return (
    <fieldset disabled={disabled}>
      <legend className="sr-only">게시글 내용</legend>
      <div className="community-form-grid">
        <div>
          <label htmlFor="post-type">글 유형</label>
          <select
            {...field("type")}
            value={values.type}
            onChange={(e) => edit("type", e.target.value)}
          >
            <option value="">선택해 주세요</option>
            {Object.entries(POST_TYPES).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
          {errorText("type")}
        </div>
        <div>
          <label htmlFor="post-topic">주제</label>
          <select
            {...field("topic")}
            value={values.topic}
            onChange={(e) => edit("topic", e.target.value)}
          >
            <option value="">선택해 주세요</option>
            {Object.entries(POST_TOPICS).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
          {errorText("topic")}
        </div>
      </div>
      <div>
        <div className="community-field-heading">
          <label htmlFor="post-title">제목</label>
          <span>{characterCount(normalizeTitle(values.title))} / 120자</span>
        </div>
        <input
          {...field("title")}
          value={values.title}
          placeholder="제목을 입력하세요."
          onChange={(e) => edit("title", e.target.value)}
        />
        {errorText("title")}
      </div>
      <div>
        <div className="community-field-heading">
          <label htmlFor="post-content">내용</label>
          <span>{characterCount(values.content)} / 10,000자</span>
        </div>
        <textarea
          {...field("content")}
          value={values.content}
          rows={12}
          placeholder="내용을 입력하세요."
          onChange={(e) => edit("content", e.target.value)}
        />
        {errorText("content")}
      </div>
    </fieldset>
  )
}
