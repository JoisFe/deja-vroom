<div align="center">

# Déjà Vroom

**처음 가는 갈림길이, 이미 본 장면으로 다시 나와요.**

*Déjà vu(데자뷰) + Vroom(부릉)*

Pleos Connect(Android Automotive) 차량 앱 · 현대자동차그룹 차량용 앱 개발 공모전 출품작

[서비스](#서비스) · [시연 시나리오](docs/scenario.md) · [구성도](docs/architecture.md) · [코드 컨벤션](docs/conventions.md) · [Discussions](../../discussions)

</div>

> [!NOTE]
> **현재 상태: 기획과 시연 시나리오 단계 (2026-11-14).** 앱 코드는 다듬은 뒤 PR로 공개하고, release APK는 [`apk/`](apk/)에 올립니다. 아래에서 "예정"이라고 적은 것은 아직 동작하지 않습니다.

## 이름

| | |
|---|---|
| 이름 | **Déjà Vroom** (데자 부룸) |
| 말장난 | *Déjà vu*(이미 본 것 같은 느낌) + *Vroom*(부릉, 차 소리) |
| 우리 얘기 | 처음 가는 갈림길인데, 1 km 전에 차가 미리 들려주고 신호 대기 때 한 장 보여 줘서 **도착하면 이미 본 장면**이 돼요 |

## 서비스

처음 가는 길이 무서운 사람은 출발 전에 로드뷰로 경로를 훑어봐요. 그런데 회전 스무 개를 다 외울 수는 없고, 두 시간 뒤 열두 번째 교차로는 기억에 남지 않아요. 가장 무서운 도착지 주차장 입구는 로드뷰에 나오지도 않고요.

Déjà Vroom은 **외우게 하지 않아요.** 볼 곳은 차가 고르고, 필요한 순간에만 꺼내 줘요.

```mermaid
flowchart LR
    A["출발 전 · P<br/>볼 곳 2곳만"] --> B["주행 · D<br/>1 km 전 한 문장"]
    B --> C["신호 대기<br/>다음 지점 사진 1장"]
    C --> D["처음 가는 갈림길<br/>'여기부터 처음이에요'"]
    D --> E["도착 60초 전<br/>입구 안내"]
    E --> F["지하 주차<br/>조향각으로 층 추정"]
    F --> G["도착 후 · P<br/>어디서 망설였나"]
```

| 무엇을 | 어떻게 | 차만 아는 신호 |
|---|---|---|
| **고른다** | 경로의 회전 중 내가 처음 가는 곳, 틀리면 크게 돌아가는 곳, 입구를 지나치기 쉬운 곳만 고름 | 내 주행 이력, NaviHelper 재탐색 이벤트 |
| **꺼낸다** | 1 km 전, 한가한 순간(지시등 OFF, 조향 작음, 다음 안내 300 m 넘게 남음)에 한 문장. 멈췄을 때 사진 한 장 | 지시등, 조향각, 브레이크, 기어, 차속 |
| **줄인다** | 도착 뒤 지점별로 "깜빡이를 켜고 차선을 바꾸기까지 몇 초"를 보여 주고, 익숙해진 곳은 다음부터 말하지 않음 | 지시등과 조향의 타임라인 |

**하지 않는 것:** 내비가 이미 하는 말(사고다발, 스쿨존, 단속카메라)은 반복하지 않아요. 상태 대신 행동을 말해요("사고 많은 곳" ✕ → "지금 2차로, 왼쪽은 유턴 전용" ○). 아는 길에서는 침묵해요. 속도와 급제동은 기록하지 않아요. 운전 점수가 아니라 **익숙해지는 정도**를 보여 주는 앱이에요.

자세한 규칙: [서비스 컨셉](docs/concept.md) · 장면별 흐름: [시연 시나리오](docs/scenario.md)

## 실행 방법

> [!IMPORTANT]
> 앱 소스는 아직 이 저장소에 없습니다(공개 예정). 소스가 올라오면 아래 명령으로 빌드합니다.

```bash
git clone https://github.com/JoisFe/deja-vroom.git
cd deja-vroom
./gradlew :app:assembleDebug        # 디버그 빌드
./gradlew :app:testDebugUnitTest    # 단위 테스트
./gradlew :app:installDebug         # 실행 중인 에뮬레이터에 설치
```

시연은 **Demo 모드**로 실행해요. 경로, 볼 곳, 사진은 가짜 데이터이고 화면에 Demo라고 표시해요. 차량 신호는 Pleos Virtual Console로 넣어요(순서: [시연 시나리오 §시연 순서](docs/scenario.md#virtual-console-시연-순서)).

## 테스트 환경과 필요한 설정

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator 2.1.0, Android API 34 시스템 이미지 |
| 차량 신호 입력 | Pleos Virtual Console (Gear, ACCEL, Brake, Steering, Turn Signal, 내비 TBT 주입) |
| 앱 | Kotlin, Jetpack Compose, minSdk 28 |
| 빌드 | Gradle Wrapper (`./gradlew`), Gradle 실행 JDK 17 또는 21 |
| Pleos SDK | Vehicle, NaviHelper 3.0.5, TextToSpeech. Pleos Playground 프로젝트 승인 필요 |
| 비밀 값 | Demo 모드는 필요 없어요. 실제 연동 시 `secrets.properties`(git 제외)에 넣어요 |

## APK

| 항목 | 위치 |
|---|---|
| release APK | [`apk/`](apk/) 폴더 (공모전 규칙에 따른 파일명 `팀명_앱이름_release.apk`, 업로드 예정) |
| 용량이 100 MB를 넘을 때 | [GitHub Releases](../../releases) |

## 구성도

```mermaid
flowchart TB
    subgraph Car["차량 (Pleos Connect)"]
        V["Vehicle SDK<br/>TurnSignal · SteeringWheel · Brake"]
        A["AAOS CarPropertyManager<br/>GEAR_SELECTION · 차속"]
        N["NaviHelper 3.0.5<br/>TBT · 재탐색 · 현재 위치"]
    end
    subgraph App["Déjà Vroom 앱"]
        S["신호 수집<br/>(Repository, SDK는 여기서만 호출)"]
        P["볼 곳 선정<br/>점수 + 상한"]
        T["타이밍 판정<br/>한가함 · 정차 · 서행"]
        H["이력 · 복기<br/>처음 · 익숙함"]
        UI["Compose 화면<br/>P 카드 · 정차 카드 · 복기"]
    end
    O["TTS<br/>한 문장 안내"]
    X["외부 경로 · 로드뷰 API<br/>(약관 확인 후)"]

    V & A & N --> S --> P & T & H
    X -.-> P
    P & T --> UI
    T --> O
    H --> UI
```

계층, SDK 매핑, 한계는 [구성도 문서](docs/architecture.md)에 있어요.

## 문서

| 문서 | 내용 |
|---|---|
| [서비스 컨셉](docs/concept.md) | 문제, 해법, 볼 곳 고르는 규칙, 하지 않는 것 |
| [시연 시나리오](docs/scenario.md) | 일곱 장면과 Virtual Console 시연 순서(7분) |
| [구성도](docs/architecture.md) | 앱 계층, Pleos SDK·API 매핑, 알려진 한계 |
| [코드 컨벤션](docs/conventions.md) | Kotlin·Compose 규칙, 브랜치·커밋·PR |
| [개발 흐름](docs/workflow.md) | Spec Kit 스펙 → Claude Code 하네스 구현 → 에뮬레이터 검증 |
| [제출물 체크리스트](docs/submission.md) | 공모전 제출물, 블라인드 점검, 라이선스 |
| [Claude Code 하네스](.claude/README.md) | 이 저장소의 에이전트·스킬·규칙 |

## 개발 방식

스펙은 [GitHub Spec Kit](https://github.com/github/spec-kit)(한국어 템플릿)으로 쓰고, 구현과 검증은 [Claude Code](https://claude.com/claude-code) 하네스로 해요. "완료"는 빌드·테스트 통과 **그리고** 에뮬레이터에서 스펙의 성공 기준을 확인한 상태예요. 원칙은 [헌법](.specify/memory/constitution.md)에 있어요.

```text
/speckit-specify → /speckit-clarify → /speckit-plan → /speckit-tasks
        → /new-feature → pleos-app-developer → /verify → app-evaluator → 리뷰 → PR
```

## 질문과 피드백

[Discussions](../../discussions)에 소개, 코드 컨벤션, 시연 영상, 로드맵을 올려 두었어요. 버그와 기능 제안은 [Issues](../../issues)에 남겨 주세요.
