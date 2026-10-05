# 보청기 급여제품 목록 출처

- 자료: 보건복지부고시 제2026-86호, 2026-04-16 시행.
- [고시 원문](https://www.nhis.or.kr/lm/lmxsrv/law/lawFullContent.do?SEQ=1619&SEQ_HISTORY=613524)
- 원문 확인 및 전사 검토: 2026-09-23.
- 범위: 공식 고시의 I–IV군 206개 (23 / 25 / 94 / 64).
- 방식: 공개 표 이미지의 칸별 문자 인식 후 원문 시각 대조 및 오인식 교정.
- 업체명은 같은 법인의 줄바꿈·공백을 통일했다. 가격은 고시가격이며 지원금 또는 실제 판매가격이 아니다.
- sourceRow는 표 이미지의 0부터 시작하는 행 위치다. 첫 표의 머리글도 행으로 센다.
- 이 목록은 고정된 고시 사본이다. 실시간 API나 주기 갱신 작업은 없다. 사용성 테스트용이며 운영 데이터 갱신을 약속하지 않는다.

## 원본 표 이미지와 무결성 기록

- [163550357](https://www.nhis.or.kr/lm/lmxsrv/law/lawImg.do?SEQ_HISTORY=613524&ID=163550357) — SHA-256 `57caa24fa0cd320c55f13af42a41023b337db6921a464d990798207099580355`
- [163550359](https://www.nhis.or.kr/lm/lmxsrv/law/lawImg.do?SEQ_HISTORY=613524&ID=163550359) — SHA-256 `fa280363481101aa006778720afd981e45e5b444bccbb660cfc3ce5f6ed394ed`
- [163550361](https://www.nhis.or.kr/lm/lmxsrv/law/lawImg.do?SEQ_HISTORY=613524&ID=163550361) — SHA-256 `b08458d4192e7a11b7fde0e3deaedd624bdbd0a905a2ce6bf7828c3abd8fef52`
- [163550217](https://www.nhis.or.kr/lm/lmxsrv/law/lawImg.do?SEQ_HISTORY=613524&ID=163550217) — SHA-256 `b69e6bd673ca5faa582c21f0708f26caa896f12f3fd5817722a305572694ac17`
- [163550379](https://www.nhis.or.kr/lm/lmxsrv/law/lawImg.do?SEQ_HISTORY=613524&ID=163550379) — SHA-256 `5f352bb78b37e44a81a7e57ccced1ed8850b614e17217d73847c744f38dc3aa1`
- [163550381](https://www.nhis.or.kr/lm/lmxsrv/law/lawImg.do?SEQ_HISTORY=613524&ID=163550381) — SHA-256 `5e74f79838e7fbab82ba8df113329c02a1e0c0c6f606a7a1eef6cbe88297fa0c`
- [163550383](https://www.nhis.or.kr/lm/lmxsrv/law/lawImg.do?SEQ_HISTORY=613524&ID=163550383) — SHA-256 `fd920a7dcdd2b1bd2a82c86408b3ab62353cf2dce135387bb4c14af668537974`
- [163550385](https://www.nhis.or.kr/lm/lmxsrv/law/lawImg.do?SEQ_HISTORY=613524&ID=163550385) — SHA-256 `20da39ea5a27e01658050efeffc71c56a3de22bab512168cda702aee67e6bcb5`
- [163550363](https://www.nhis.or.kr/lm/lmxsrv/law/lawImg.do?SEQ_HISTORY=613524&ID=163550363) — SHA-256 `292d1a63b86bfd45b84c9e32d4f08c58b1adbee33e316255b57cf14b921296c8`
- [163550365](https://www.nhis.or.kr/lm/lmxsrv/law/lawImg.do?SEQ_HISTORY=613524&ID=163550365) — SHA-256 `a8db3a2a8320b6f68d352eb1df7209c924d78ee7d4666209fcc9b8cb8ea0c0dd`
- [163550367](https://www.nhis.or.kr/lm/lmxsrv/law/lawImg.do?SEQ_HISTORY=613524&ID=163550367) — SHA-256 `8e094d60dd1179a04bd38819968bb97b648523a8da541d8044571132d5f12e9b`

## 교체 방법

새 고시의 전체 제품표를 확보하여 모델명·제품코드·업체명·가격을 검증한다. 제품 추가뿐 아니라 삭제·가격 변경도 반영하고, 시행일과 실제 확인일을 따로 갱신한다. JSON을 교체한 뒤 중복 코드·전체/제품군별 수·검색 회귀를 `npm run test:products`로 확인한다. 새 고시로 수량·가격이 바뀌었다면 검증 스크립트의 기대값도 원문과 함께 검토한다. 확인일만 자동으로 바꾸지 않는다.
