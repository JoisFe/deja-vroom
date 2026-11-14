# 빠른 실행 및 검증

## Demo 실행

1. Android Studio에서 프로젝트를 열거나 저장소의 Gradle wrapper를 사용한다.
2. API 키 없이 `:app:assembleDebug` 및 `:app:installDebug`를 실행한다.
3. 앱에서 기본 목적지 강남역을 선택해 Summary → Briefing → Complete → Mock Navigation 흐름을 시연한다.
4. 구간의 `데모 일러스트` 및 미검증 정보 고지를 확인한다.

## 자동 검증

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
  .claude/skills/verify/scripts/gradle-quiet.sh :app:assembleDebug :app:testDebugUnitTest
```

## 실제 연결 전 체크리스트

- Pleos 공식 프로젝트/SDK 접근 및 실제 선택 경로 geometry 계약을 확인한다.
- 차량 내 표시/공모전 배포 사용권 및 provider 키 제한을 확인한다.
- 사진의 좌표·촬영일·방향·attribution을 route matching evidence와 함께 제공한다.
- 검증 실패, 미지원, 권한 없음, network 실패 각각을 Fake로 테스트한다.
- 일반 Android emulator 성공을 Pleos emulator/실차 성공으로 보고하지 않는다.
