# 구현 계획: FirstLook 브리핑 근거·상태 고도화

**기능 디렉터리**: `specs/002-firstlook-mvp-hardening/` | **작성일**: 2026-11-11 | **스펙**: [spec.md](spec.md)

## 요약

현재 Demo 흐름은 유지하면서 구간 후보와 동일 경로 geometry의 매칭, provider lookup 상태와 view 인덱스, 음성 출력 생명주기, 주행 제한 정책을 Fake로 검증 가능하게 연결한다. Pleos SDK 및 도로 사진 공급자는 사용권·프로젝트 권한을 확인하기 전까지 실제 구현으로 주장하지 않는다.

## 기술 맥락

| 항목 | 값 |
|---|---|
| 언어/UI | Kotlin + Compose |
| 아키텍처 | 기존 단일 Activity + ViewModel/StateFlow + repository 계약 유지 |
| DI | 현재 constructor 기반 생성 유지; Hilt 미도입 |
| 테스트 | JUnit4 및 현재 fake 구현 |
| 실행 환경 | Android SDK 35; Pleos runtime 별도 |
| 외부 서버 | 없음 |

## 헌법 점검

| 원칙 | 결과 | 메모 |
|---|---|---|
| I. 공모전 범위 우선 | 통과 | P1 브리핑 근거·상태, P2 음성/restriction만 구현 |
| II. 차량 특화와 주행 안전 | 통과 | 제한 상태 Fake; 실제 어댑터는 capability/SDK 미확인으로 추가하지 않음 |
| III. 표준 아키텍처 | 부분 적용 | 기존 단일 모듈/생성 구조 유지, 새로운 계층이나 DI 추가 없음 |
| IV. 검증된 완료 | 통과 조건 | SDK가 설정된 환경에서 `/verify`와 가능한 경우 AVD 평가 필요 |
| V. 보안과 블라인드 심사 | 통과 | API 키, 위치 로그, 식별 정보 추가 없음 |

## SDK·API 사용 계획

| 기능 | SDK / API | 제공 상태 |
|---|---|---|
| 사진 provider | 기존 `RoadViewProvider` contract | Demo/Unconfigured Fake만 |
| 음성 출력 | `BriefingSpeechOutput` | Fake/No-op만; Pleos TextToSpeech 미연동 |
| 운전 제한 | `DrivingRestrictionSource` contract | Fake만; 실제 CarUxRestrictions 어댑터 미연동 |
| 경로 geometry | 순수 Kotlin matcher | synthetic test fixture; 실제 source 미연동 |

## 화면과 상태

| 화면 | 주요 상태 | 제한 상태 |
|---|---|---|
| 요약/브리핑 | route, lookup 결과, view index, attribution/capture date | 목적지/이미지 조작 제한 |
| 제한 모드 | driving restriction 안내 | 음성 중심의 정적 안내 |

## 시연 시나리오

| 순서 | 조작 | 결과 |
|---|---|---|
| 1 | Demo 목적지 미리보기 | curated Demo 3개 구간 표시 |
| 2 | 브리핑 진입/구간 이동 | Demo artwork 출처와 미검증 상태 표시, 음성 Fake lifecycle 반응 |
| 3 | Fake restriction ON | 입력/시점 이동 차단 계약을 단위 테스트로 확인 |

## 구조 결정

기존 `model/BriefingModels.kt`, `data/RouteAnalyzer.kt`, `data/RoadViewProvider.kt`, `feature/FirstLookViewModel.kt`, `MainActivity.kt`를 확장하고 순수 `RouteGeometryMatcher.kt` 및 제한 상태 계약/테스트만 추가한다. 공급자별 WebView, 지도 SDK, 네트워크 의존성은 차량 내 표시 권리가 불명확해 추가하지 않는다.
