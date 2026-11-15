# APK

- 파일: `deja_vroom_deja_vroom_release.apk`
- 기준 소스: `f28c18b`의 앱 코드 (origin/main)
- release 빌드이며 팀의 별도 키로 서명했습니다.
- 로드뷰 및 장소 검색 페이지: `https://deja-vroom-roadview.vercel.app/roadview/`
- 카카오 로드뷰는 배포된 페이지에서 표시를 확인했습니다. APK 전체 흐름은 별도 검증이 필요합니다.
- 서명 키는 저장소 밖에 보관하며, 같은 앱의 업데이트에는 동일한 키가 필요합니다.
- Vercel 페이지는 수동 배포되어 있습니다. 소스 변경이나 카카오 키 변경 시 페이지를 다시 배포해야 합니다.

## 서명

`./gradlew :app:assembleRelease`는 저장소 루트의 `keystore.properties`를 읽어 서명한다. 이 파일이 없으면 APK는 서명되지 않은 채(`app-release-unsigned.apk`) 나온다. 빌드는 통과하지만 설치할 수 없다.

1. 팀 키를 받는다. 키가 없을 때만 새로 만들고, 키 파일은 저장소 밖에 둔다.

   ```bash
   keytool -genkeypair -v -keystore ~/keys/deja-vroom-release.jks -alias deja-vroom \
     -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Deja Vroom, O=Deja Vroom, C=KR"
   ```

2. 저장소 루트에 `keystore.properties`를 만든다. git이 무시하는 파일이다.

   ```properties
   storeFile=/Users/<계정>/keys/deja-vroom-release.jks
   storePassword=<키 저장소 비밀번호>
   keyAlias=deja-vroom
   keyPassword=<키 비밀번호>
   ```

3. `./gradlew :app:assembleRelease`로 빌드한다. 서명된 APK는 `app/build/outputs/apk/release/app-release.apk`다. 이 파일을 위 파일명으로 이 폴더에 복사한다.

| 키 | 값 |
|---|---|
| `storeFile` | 키 파일 경로. 상대 경로는 저장소 루트 기준이다 |
| `storePassword` | 키 저장소 비밀번호 |
| `keyAlias` | 키를 만들 때 `-alias`에 준 이름 |
| `keyPassword` | 키 비밀번호 |

네 값이 모두 있어야 서명한다. 하나라도 비면 서명하지 않는다.

> 인증서 이름(`-dname`)은 APK를 받은 누구나 `keytool -printcert -jarfile`로 볼 수 있다. 실명이나 소속을 넣지 않고 팀 이름만 쓴다. 키 파일과 `keystore.properties`는 커밋하지 않는다.
