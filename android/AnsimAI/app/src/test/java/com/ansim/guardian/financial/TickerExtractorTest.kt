package com.ansim.guardian.financial

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TickerExtractorTest {

    @Test
    fun `알려진 주식명이 추출된다`() {
        val entities = TickerExtractor.extract("삼성전자 주식 지금 사면 됩니다")
        assertThat(entities).isNotEmpty()
        assertThat(entities.any { it.text == "삼성전자" }).isTrue()
        assertThat(entities.first { it.text == "삼성전자" }.type)
            .isEqualTo(TickerExtractor.EntityType.STOCK)
    }

    @Test
    fun `6자리 종목 코드가 추출된다`() {
        val entities = TickerExtractor.extract("005930 지금 매수하세요 상한가 갑니다")
        assertThat(entities.any { it.ticker == "005930" }).isTrue()
    }

    @Test
    fun `비트코인이 코인으로 추출된다`() {
        val entities = TickerExtractor.extract("비트코인 투자하세요 지금 바로")
        assertThat(entities).isNotEmpty()
        assertThat(entities.first().type).isEqualTo(TickerExtractor.EntityType.COIN)
    }

    @Test
    fun `영문 코인명이 추출된다`() {
        val entities = TickerExtractor.extract("BTC 투자 수익 보장합니다")
        assertThat(entities.any {
            it.type == TickerExtractor.EntityType.COIN
        }).isTrue()
    }

    @Test
    fun `비상장주식 신호가 탐지된다`() {
        val entities = TickerExtractor.extract("이 회사 비상장이지만 곧 상장 예정입니다")
        assertThat(entities.any {
            it.type == TickerExtractor.EntityType.UNLISTED_STOCK
        }).isTrue()
    }

    @Test
    fun `투자 관련 일반 언급이 탐지된다`() {
        val entities = TickerExtractor.extract("이 주식에 투자하면 됩니다")
        assertThat(entities).isNotEmpty()
    }

    @Test
    fun `관련 없는 텍스트는 빈 목록을 반환한다`() {
        val entities = TickerExtractor.extract("오늘 점심 뭐 먹을까요")
        assertThat(entities).isEmpty()
    }

    @Test
    fun `빈 문자열도 처리된다`() {
        val entities = TickerExtractor.extract("")
        assertThat(entities).isEmpty()
    }

    @Test
    fun `중복 없이 반환된다`() {
        val entities = TickerExtractor.extract("삼성전자 삼성전자 삼성전자")
        val texts = entities.map { it.text }
        assertThat(texts.size).isEqualTo(texts.toSet().size)
    }

    @Test
    fun `여러 종목이 한 문장에 있으면 모두 추출된다`() {
        val entities = TickerExtractor.extract("삼성전자랑 카카오 지금 매수하세요")
        val names = entities.map { it.text }
        assertThat(names).contains("삼성전자")
        assertThat(names).contains("카카오")
    }

    @Test
    fun `이더리움이 코인으로 추출된다`() {
        val entities = TickerExtractor.extract("이더리움 eth 투자 권유")
        val coins = entities.filter { it.type == TickerExtractor.EntityType.COIN }
        assertThat(coins).isNotEmpty()
    }
}
