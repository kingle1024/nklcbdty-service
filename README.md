## 브랜치 네이밍

```bash
// 브랜치 네이밍
feat/#12/postDetail (x)
feat/#12/post-detail (o)
```

## Commit 컨벤션

**커밋 메세지 형식**

```
Emoji Type(#issue-num): subject
// ex) 🚨 Fix(#41): commitlint 이슈 해결
```

```bash
🚨 Fix: 수정 내용
✨ Feat: 새로운 기능 추가, 사용자 입장에서 변화가 있을 경우
🎉 Init: 프로젝트 초기 생성
📝 Chore: 그 외 자잘한 수정에 대한 커밋, 주석, 의존성 설치, 리드미 수정
💄 Style: CSS, styled-component 스타일 관련 변경
🔨 Refactor: 코드 리팩토링에 대한 커밋, 사용자 입장에서 변화가 없는 코드, 파일명 폴더명 변경 및 이동
🗑️ Remove: 파일을 삭제하는 작업만 수행하는 경우
```

## 기술 스택

|       Language       |                                                                                                                                         ![a](https://img.shields.io/badge/TypeScript-3178C6?style=flat-square&logo=typescript&logoColor=white)                                                                                                                                         |
| :------------------: | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------: |
|     **Library**      |                                                                                                                                            ![a](https://img.shields.io/badge/Next.js-white?style=flat-square&logo=Next.js&logoColor=black)                                                                                                                                             |
|      **Build**       |                                                                                                                                                ![a](https://img.shields.io/badge/SWC-white?style=flat-square&logo=SWC&logoColor=black)                                                                                                                                                 |
|     **Package**      |                                                                                                                                               ![a](https://img.shields.io/badge/pnpm-F69220?style=flat-square&logo=pnpm&logoColor=black)                                                                                                                                               |
|       **Http**       |                                                                                                                                              ![a](https://img.shields.io/badge/axios-5A29E4?style=flat-square&logo=axios&logoColor=white)                                                                                                                                              |
| **State Management** |                                                                                                                                            ![a](https://img.shields.io/badge/zustand-4A154B?style=flat-square&logo=zustand&logoColor=white)                                                                                                                                            |
|  **Data Fetching**   |                                                                                                                                       ![a](https://img.shields.io/badge/TanstackQuery-FF4154?style=flat-square&logo=reactquery&logoColor=white)                                                                                                                                        |
|      **Style**       |                                                                                                                                        ![a](https://img.shields.io/badge/TailwindCSS-06B6D4?style=flat-square&logo=TailwindCSS&logoColor=white)                                                                                                                                        |
|   **Code Format**    |                                                                                           ![a](https://img.shields.io/badge/ESlint-4B32C3?style=flat-square&logo=eslint&logoColor=white) ![a](https://img.shields.io/badge/Prettier-F7B93E?style=flat-square&logo=Prettier&logoColor=black)                                                                                            |
|      **Deploy**      |                                                                                                                                             ![a](https://img.shields.io/badge/vercel-000000?style=flat-square&logo=vercel&logoColor=white)                                                                                                                                             |
|     **Co-work**      | ![a](https://img.shields.io/badge/Gather-5865F2?style=flat-square&logo=Gather&logoColor=white)![a](https://img.shields.io/badge/github-181717?style=flat-square&logo=github&logoColor=white)![a](https://img.shields.io/badge/Notion-000000?style=flat-square&logo=notion&logoColor=white)![a](https://img.shields.io/badge/slack-4A154B?style=flat-square&logo=slack&logoColor=white) |

## 디렉토리 구조

```
📦public
┗ 📦assets -> 정적 파일들
📦src
┣ 📦app -> 라우팅 용도
┣ ┣ 📦[dynamic] -> 동적 라우팅
┣ 📦components -> 컴포넌트
┣ ┣ 📦_common -> 공통 컴포넌트
┣ ┗ 📦containers -> 지역적인 UI 컴포넌트
┣ 📦constants -> 상수
┣ 📦hooks -> 커스텀 훅
┣ 📦services -> api 요청
┣ ┣ 📦types ->
┣ 📦stores -> Zustand Store
┗ 📦utils -> 유틸함수
```
