package com.ansim.guardian.ai.llm

import android.content.Context
import com.ansim.guardian.BuildConfig

// DART API 키 관리
//
// 키 설정 방법 (개발자만 하면 됨, 사용자는 아무것도 안 해도 됨):
//   1. opendart.fss.or.kr 에서 무료 API 키 발급
//   2. 프로젝트 루트의 local.properties 파일에 한 줄 추가:
//      DART_API_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
//   3. 빌드하면 BuildConfig.DART_API_KEY 에 자동 주입됨
//
// local.properties는 .gitignore에 포함되어 있으므로 키가 GitHub에 올라가지 않음
//
// 실 서비스 배포 시: 백엔드 서버에서 키 관리 → 앱은 서버 API 호출 방식으로 전환
object DartApiKeyStore {
    fun getKey(context: Context): String {
        // 1순위: BuildConfig (개발자가 local.properties에 설정)
        if (BuildConfig.DART_API_KEY.isNotBlank()) {
            return BuildConfig.DART_API_KEY
        }
        // 2순위: 테스트/데모용 임시 키 (선택사항)
        return ""
    }
}
