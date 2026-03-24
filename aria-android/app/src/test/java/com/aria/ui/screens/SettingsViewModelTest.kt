package com.aria.ui.screens

import com.aria.data.memory.Mem0Repository
import com.aria.data.repository.SecureStorage
import com.aria.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    // MockK can mock concrete classes; SecureStorage requires Android context so we mock it here
    private val secureStorage = mockk<SecureStorage>(relaxed = true)
    private val mem0Repository = mockk<Mem0Repository>(relaxed = true)
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        // Stub getApiKey for the KEY_USER_NAME call made inside the ViewModel constructor
        every { secureStorage.getApiKey(any()) } returns null
        viewModel = SettingsViewModel(secureStorage, mem0Repository)
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initialUserName_isEmptyWhenStorageReturnsNull`() {
        assertEquals(
            "userName should be empty string when storage returns null",
            "",
            viewModel.userName.value
        )
    }

    @Test
    fun `initialUserName_reflectsStoredValue`() {
        every { secureStorage.getApiKey(SecureStorage.KEY_USER_NAME) } returns "Alice"
        val vm = SettingsViewModel(secureStorage, mem0Repository)
        assertEquals(
            "userName should reflect the value returned by storage",
            "Alice",
            vm.userName.value
        )
    }

    @Test
    fun `initialSnackbar_isNull`() {
        assertNull(
            "snackbarMessage should be null on init",
            viewModel.snackbarMessage.value
        )
    }

    @Test
    fun `initialIsWiping_isFalse`() {
        assertFalse(
            "isWiping should be false on init",
            viewModel.isWiping.value
        )
    }

    @Test
    fun `initialExportData_isNull`() {
        assertNull(
            "exportData should be null on init",
            viewModel.exportData.value
        )
    }

    // -------------------------------------------------------------------------
    // saveKey
    // -------------------------------------------------------------------------

    @Test
    fun `saveKey_callsSecureStorageSaveApiKey`() = runTest {
        viewModel.saveKey(SecureStorage.KEY_CLAUDE_API, "sk-test-123")
        verify { secureStorage.saveApiKey(SecureStorage.KEY_CLAUDE_API, "sk-test-123") }
    }

    @Test
    fun `saveKey_trimsWhitespace_beforeSaving`() = runTest {
        viewModel.saveKey(SecureStorage.KEY_MEM0_API, "  my-key  ")
        verify { secureStorage.saveApiKey(SecureStorage.KEY_MEM0_API, "my-key") }
    }

    @Test
    fun `saveKey_setsSnackbarToSaved_onSuccess`() = runTest {
        viewModel.saveKey(SecureStorage.KEY_TELEGRAM_BOT_TOKEN, "bot-token")
        assertEquals(
            "snackbarMessage should be 'Saved' after successful saveKey",
            "Saved",
            viewModel.snackbarMessage.value
        )
    }

    @Test
    fun `saveKey_forUserName_updatesUserNameStateFlow`() = runTest {
        viewModel.saveKey(SecureStorage.KEY_USER_NAME, "Bob")
        assertEquals(
            "userName StateFlow should update when KEY_USER_NAME is saved",
            "Bob",
            viewModel.userName.value
        )
    }

    @Test
    fun `saveKey_onStorageException_setsSnackbarToErrorMessage`() = runTest {
        every { secureStorage.saveApiKey(any(), any()) } throws RuntimeException("Keystore locked")

        viewModel.saveKey(SecureStorage.KEY_CLAUDE_API, "bad-key")

        assertEquals(
            "snackbarMessage should contain the exception message",
            "Keystore locked",
            viewModel.snackbarMessage.value
        )
    }

    // -------------------------------------------------------------------------
    // getKey
    // -------------------------------------------------------------------------

    @Test
    fun `getKey_returnsStoredValue`() {
        every { secureStorage.getApiKey(SecureStorage.KEY_MEM0_API) } returns "mem0-key-xyz"

        val result = viewModel.getKey(SecureStorage.KEY_MEM0_API)

        assertEquals(
            "getKey should return the value from storage",
            "mem0-key-xyz",
            result
        )
    }

    @Test
    fun `getKey_returnsEmptyString_whenStorageReturnsNull`() {
        every { secureStorage.getApiKey(SecureStorage.KEY_DEEPGRAM_API) } returns null

        val result = viewModel.getKey(SecureStorage.KEY_DEEPGRAM_API)

        assertEquals(
            "getKey should return empty string when storage returns null",
            "",
            result
        )
    }

    // -------------------------------------------------------------------------
    // clearSnackbar
    // -------------------------------------------------------------------------

    @Test
    fun `clearSnackbar_setsSnackbarMessageToNull`() = runTest {
        // Set a message first
        viewModel.saveKey(SecureStorage.KEY_LIVEKIT_URL, "wss://host")
        assertNotNull("Precondition: snackbarMessage should be set", viewModel.snackbarMessage.value)

        viewModel.clearSnackbar()

        assertNull(
            "snackbarMessage should be null after clearSnackbar",
            viewModel.snackbarMessage.value
        )
    }

    // -------------------------------------------------------------------------
    // clearExport
    // -------------------------------------------------------------------------

    @Test
    fun `clearExport_setsExportDataToNull`() = runTest {
        // Trigger exportProfile first so exportData is non-null
        coEvery { mem0Repository.exportProfile() } returns """{"results":[]}"""
        viewModel.exportProfile()
        assertNotNull("Precondition: exportData should be populated", viewModel.exportData.value)

        viewModel.clearExport()

        assertNull(
            "exportData should be null after clearExport",
            viewModel.exportData.value
        )
    }

    // -------------------------------------------------------------------------
    // exportProfile
    // -------------------------------------------------------------------------

    @Test
    fun `exportProfile_populatesExportData`() = runTest {
        val fakeJson = """{"results":[{"id":"x"}]}"""
        coEvery { mem0Repository.exportProfile() } returns fakeJson

        viewModel.exportProfile()

        assertEquals(
            "exportData should be set to the JSON returned by the repository",
            fakeJson,
            viewModel.exportData.value
        )
    }

    @Test
    fun `exportProfile_onException_setsSnackbarMessage`() = runTest {
        coEvery { mem0Repository.exportProfile() } throws RuntimeException("Network error")

        viewModel.exportProfile()

        assertEquals(
            "snackbarMessage should contain exception message on export failure",
            "Network error",
            viewModel.snackbarMessage.value
        )
        assertNull(
            "exportData should remain null on failure",
            viewModel.exportData.value
        )
    }

    // -------------------------------------------------------------------------
    // wipeAll
    // -------------------------------------------------------------------------

    @Test
    fun `wipeAll_callsSecureStorageClearAll`() = runTest {
        viewModel.wipeAll()
        verify { secureStorage.clearAll() }
    }

    @Test
    fun `wipeAll_callsRepositoryWipeProfile`() = runTest {
        viewModel.wipeAll()
        coVerify { mem0Repository.wipeProfile() }
    }

    @Test
    fun `wipeAll_setsSnackbarToProfileWiped`() = runTest {
        viewModel.wipeAll()
        assertEquals(
            "snackbarMessage should be 'Profile wiped' after successful wipe",
            "Profile wiped",
            viewModel.snackbarMessage.value
        )
    }

    @Test
    fun `wipeAll_isWiping_isFalseAfterCompletion`() = runTest {
        viewModel.wipeAll()
        assertFalse(
            "isWiping should be false after wipeAll completes",
            viewModel.isWiping.value
        )
    }

    @Test
    fun `wipeAll_onException_isWiping_isFalseInFinally`() = runTest {
        coEvery { mem0Repository.wipeProfile() } throws RuntimeException("Delete failed")

        viewModel.wipeAll()

        assertFalse(
            "isWiping should be false even when wipeProfile throws",
            viewModel.isWiping.value
        )
        assertEquals(
            "snackbarMessage should contain exception message",
            "Delete failed",
            viewModel.snackbarMessage.value
        )
    }
}
