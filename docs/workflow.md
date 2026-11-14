# 개발 흐름

**무엇을, 왜**는 GitHub Spec Kit이 정하고, **어떻게, 언제 완료인지**는 Claude Code 하네스가 정해요.

```mermaid
flowchart LR
    subgraph Spec["Spec Kit (한국어 템플릿)"]
        S1["/speckit-specify"] --> S2["/speckit-clarify"] --> S3["/speckit-plan"] --> S4["/speckit-tasks"] --> S5["/speckit-analyze"]
    end
    subgraph Build["Claude Code 하네스"]
        B1["/new-feature<br/>브랜치 생성"] --> B2["pleos-app-developer<br/>구현"] --> B3["/verify<br/>빌드·테스트"] --> B4["app-evaluator<br/>에뮬레이터 확인"] --> B5["/speckit-converge"] --> B6["/review → PR"]
    end
    S5 -->|tasks.md| B1
    B4 -->|FAIL: 고칠 곳| B2
```

| 단계 | 명령 · 에이전트 | 산출물 |
|---|---|---|
| 스펙 | `/speckit-specify` → `/speckit-clarify` | `specs/NNN-기능/spec.md` (성공 기준 SC-*) |
| 계획 | `/speckit-plan` (+ `/speckit-checklist`) | `plan.md` |
| 작업 분해 | `/speckit-tasks` → `/speckit-analyze` | `tasks.md` |
| 구현 | `/new-feature specs/NNN-기능` → `pleos-app-developer` | 코드 + 테스트 |
| 검증 | `/verify` | 빌드·테스트 결과 (실패만 보고) |
| 평가 | `app-evaluator` | SC-*별 PASS/FAIL, 스크린샷 |
| 마무리 | `/speckit-converge` → `/review` → PR | 남은 작업, 리뷰 결과 |

## 완료의 정의

1. `/verify`가 종료 코드 0으로 끝나요.
2. `app-evaluator`가 에뮬레이터에서 스펙의 성공 기준을 모두 PASS로 확인해요.
3. 리뷰에서 HIGH 지적이 없어요.

코드를 읽고 "될 것 같다"로 완료를 선언하지 않아요.

원칙: [헌법](../.specify/memory/constitution.md) · 하네스 지도: [.claude/README.md](../.claude/README.md)
