package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * OpenAI Responses/chat 兼容解析的 usage 计数门测试（评审 P1-5/P2-1）：
 * - 按字段存在判断：显式全零 payload 也是“已观察到的 usage”（返回非 null，
 *   字段为 0），不按 “>0” 过滤；
 * - 全程 Long 解析，只在旧 UI 计数边界饱和 Int（不回绕为负）；
 * - usage 对象完全缺失/无任何相关字段 → null（未观察到）。
 */
class OpenAIResponsesPayloadAdapterTest {

    @Test
    fun `explicit zero payload is observed usage with zero fields`() {
        val counts =
            OpenAIResponsesPayloadAdapter.parseUsageCounts(
                JSONObject("""{"prompt_tokens": 0, "completion_tokens": 0}""")
            )!!
        assertEquals(0, counts.totalInputTokens)
        assertEquals(0, counts.outputTokens)
        assertEquals(0, counts.cachedInputTokens)
        assertEquals(0, counts.actualInputTokens)
    }

    @Test
    fun `zero cached split with non-zero totals is parsed`() {
        val counts =
            OpenAIResponsesPayloadAdapter.parseUsageCounts(
                JSONObject(
                    """{"prompt_tokens": 100, "completion_tokens": 50, "prompt_tokens_details": {"cached_tokens": 0}}"""
                )
            )!!
        assertEquals(100, counts.totalInputTokens)
        assertEquals(100, counts.actualInputTokens)
        assertEquals(0, counts.cachedInputTokens)
        assertEquals(50, counts.outputTokens)
    }

    @Test
    fun `values beyond int range saturate at the ui boundary instead of wrapping`() {
        val counts =
            OpenAIResponsesPayloadAdapter.parseUsageCounts(
                JSONObject(
                    """{"prompt_tokens": 5000000000, "completion_tokens": 4000000000}"""
                )
            )!!
        // 旧 UI 计数边界（P2-1）：饱和为 Int.MAX，绝不回绕为负
        assertEquals(Int.MAX_VALUE, counts.totalInputTokens)
        assertEquals(Int.MAX_VALUE, counts.outputTokens)
    }

    @Test
    fun `usage absent or without any token fields returns null`() {
        assertNull(OpenAIResponsesPayloadAdapter.parseUsageCounts(null))
        assertNull(OpenAIResponsesPayloadAdapter.parseUsageCounts(JSONObject("{}")))
        assertNull(
            OpenAIResponsesPayloadAdapter.parseUsageCounts(
                JSONObject("""{"other": "x"}""")
            )
        )
    }

    @Test
    fun `deepseek prompt cache hit and miss tokens are parsed correctly`() {
        val json = JSONObject(
            """
            {
                "prompt_tokens": 1500,
                "completion_tokens": 120,
                "prompt_cache_hit_tokens": 1400,
                "prompt_cache_miss_tokens": 100
            }
            """.trimIndent()
        )
        val counts = OpenAIResponsesPayloadAdapter.parseUsageCounts(json)!!
        assertEquals(1500, counts.totalInputTokens)
        assertEquals(120, counts.outputTokens)
        assertEquals(1400, counts.cachedInputTokens)
        assertEquals(100, counts.actualInputTokens)
        assertEquals(1400, counts.promptCacheHitTokens)
        assertEquals(100, counts.promptCacheMissTokens)
    }

    @Test
    fun `standard cached_tokens takes precedence when non-zero while prompt_cache_hit is also parsed`() {
        val json = JSONObject(
            """
            {
                "prompt_tokens": 2000,
                "completion_tokens": 50,
                "prompt_tokens_details": {
                    "cached_tokens": 1800
                },
                "prompt_cache_hit_tokens": 1800,
                "prompt_cache_miss_tokens": 200
            }
            """.trimIndent()
        )
        val counts = OpenAIResponsesPayloadAdapter.parseUsageCounts(json)!!
        assertEquals(2000, counts.totalInputTokens)
        assertEquals(1800, counts.cachedInputTokens)
        assertEquals(200, counts.actualInputTokens)
        assertEquals(1800, counts.promptCacheHitTokens)
        assertEquals(200, counts.promptCacheMissTokens)
    }
}
