package com.whatsappautoreply.data.remote.llm.provider

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LlmRouterTest {

    private lateinit var primary: LlmProvider
    private lateinit var secondary: LlmProvider
    private lateinit var fallback: LlmProvider
    private lateinit var router: LlmRouter

    @Before
    fun setup() {
        primary = mockk()
        secondary = mockk()
        fallback = mockk()

        coEvery { primary.name } returns "Primary"
        coEvery { primary.modelName } returns "PrimaryModel"
        
        coEvery { secondary.name } returns "Secondary"
        coEvery { secondary.modelName } returns "SecondaryModel"

        coEvery { fallback.name } returns "Fallback"
        coEvery { fallback.modelName } returns "FallbackModel"

        router = LlmRouter(listOf(primary, secondary, fallback))
    }

    @Test
    fun `test successful primary provider`() = runTest {
        coEvery { primary.generateCompletion(any(), any(), any(), any()) } returns "PrimaryResponse"

        val result = router.executeWithRouting("system", "user")

        assertEquals("PrimaryResponse", result)
        coVerify(exactly = 1) { primary.generateCompletion(any(), any(), any(), any()) }
        coVerify(exactly = 0) { secondary.generateCompletion(any(), any(), any(), any()) }
        coVerify(exactly = 0) { fallback.generateCompletion(any(), any(), any(), any()) }
    }

    @Test
    fun `test failover from primary to secondary`() = runTest {
        coEvery { primary.generateCompletion(any(), any(), any(), any()) } throws Exception("Network error")
        coEvery { secondary.generateCompletion(any(), any(), any(), any()) } returns "SecondaryResponse"

        val result = router.executeWithRouting("system", "user")

        assertEquals("SecondaryResponse", result)
        coVerify(exactly = 1) { primary.generateCompletion(any(), any(), any(), any()) }
        coVerify(exactly = 1) { secondary.generateCompletion(any(), any(), any(), any()) }
        coVerify(exactly = 0) { fallback.generateCompletion(any(), any(), any(), any()) }
    }

    @Test
    fun `test complete failover to fallback`() = runTest {
        coEvery { primary.generateCompletion(any(), any(), any(), any()) } throws Exception("Error")
        coEvery { secondary.generateCompletion(any(), any(), any(), any()) } throws Exception("Error")
        coEvery { fallback.generateCompletion(any(), any(), any(), any()) } returns "FallbackResponse"

        val result = router.executeWithRouting("system", "user")

        assertEquals("FallbackResponse", result)
        coVerify(exactly = 1) { primary.generateCompletion(any(), any(), any(), any()) }
        coVerify(exactly = 1) { secondary.generateCompletion(any(), any(), any(), any()) }
        coVerify(exactly = 1) { fallback.generateCompletion(any(), any(), any(), any()) }
    }

    @Test
    fun `test total failure returns null`() = runTest {
        coEvery { primary.generateCompletion(any(), any(), any(), any()) } throws Exception("Error")
        coEvery { secondary.generateCompletion(any(), any(), any(), any()) } throws Exception("Error")
        coEvery { fallback.generateCompletion(any(), any(), any(), any()) } throws Exception("Error")

        val result = router.executeWithRouting("system", "user")

        assertNull(result)
        coVerify(exactly = 1) { primary.generateCompletion(any(), any(), any(), any()) }
        coVerify(exactly = 1) { secondary.generateCompletion(any(), any(), any(), any()) }
        coVerify(exactly = 1) { fallback.generateCompletion(any(), any(), any(), any()) }
    }

    @Test
    fun `test health tracking skips unhealthy provider`() = runTest {
        // First request fails primary
        coEvery { primary.generateCompletion(any(), any(), any(), any()) } throws Exception("Error")
        coEvery { secondary.generateCompletion(any(), any(), any(), any()) } returns "SecondaryResponse"

        router.executeWithRouting("system", "user")

        // Second request fails primary again -> becomes unhealthy
        router.executeWithRouting("system", "user")

        // Third request should skip primary and go straight to secondary
        coEvery { secondary.generateCompletion(any(), any(), any(), any()) } returns "SecondaryResponse"
        val result = router.executeWithRouting("system", "user")

        assertEquals("SecondaryResponse", result)
        
        // Primary was called 2 times total, not 3
        coVerify(exactly = 2) { primary.generateCompletion(any(), any(), any(), any()) }
        coVerify(exactly = 3) { secondary.generateCompletion(any(), any(), any(), any()) }
    }
}
