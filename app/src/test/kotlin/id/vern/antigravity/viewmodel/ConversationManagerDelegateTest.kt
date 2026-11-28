package id.vern.antigravity.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationManagerDelegateTest {

    @Test
    fun `startNewConversation clears workspace when projectPath is empty string`() {
        val delegate = ConversationManagerDelegate()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        var activeWorkspace = "/storage/emulated/0/Projects/Demo"
        var cleared = false

        delegate.startNewConversation(
            context = null,
            projectPath = "",
            currentMessages = emptyList(),
            scope = scope,
            onWorkspaceChange = { activeWorkspace = it },
            onClearState = { cleared = true }
        )

        assertTrue(cleared)
        assertEquals("", activeWorkspace)
        assertEquals("New Conversation", delegate.conversationTitle.value)
    }

    @Test
    fun `loadConversation clears workspace for non-project conversation and restores it for project conversation`() {
        val delegate = ConversationManagerDelegate()
        var activeWorkspace = "/storage/emulated/0/Projects/Demo"

        delegate.loadConversation(
            summary = ConversationSummary(
                id = "general-1",
                title = "General Chat",
                timestamp = 1L,
                messageCount = 2,
                projectPath = null
            ),
            onWorkspaceChange = { activeWorkspace = it },
            onLog = { _, _ -> }
        )
        assertEquals("", activeWorkspace)

        delegate.loadConversation(
            summary = ConversationSummary(
                id = "proj-1",
                title = "Project Chat",
                timestamp = 2L,
                messageCount = 3,
                projectPath = "/storage/emulated/0/Projects/App"
            ),
            onWorkspaceChange = { activeWorkspace = it },
            onLog = { _, _ -> }
        )
        assertEquals("/storage/emulated/0/Projects/App", activeWorkspace)
    }

    @Test
    fun `archiveConversation preserves saved model metadata on existing summary`() {
        val delegate = ConversationManagerDelegate()
        val existing = ConversationSummary(
            id = "chat-1",
            title = "Hydrogen Chat",
            timestamp = 10L,
            messageCount = 2,
            projectPath = "/storage/emulated/0/Projects/App",
            modelName = "Gemini 4 Hydrogen",
            modelCliId = "gemini-4-hydrogen-medium",
            thinkingBudget = "Medium"
        )
        delegate.updateConversationHistoryDirect { listOf(existing) }
        delegate.setActiveConversationIdDirect("chat-1")
        delegate.setConversationTitleDirect("Hydrogen Chat")

        delegate.archiveConversation(
            context = null,
            currentMessages = listOf(
                id.vern.antigravity.data.ChatMessage(
                    id = "m1",
                    role = id.vern.antigravity.data.MessageRole.USER,
                    content = "hi"
                )
            ),
            currentWorkspacePath = "/storage/emulated/0/Projects/App"
        )

        val archived = delegate.conversationHistory.value.first { it.id == "chat-1" }
        assertEquals("Gemini 4 Hydrogen", archived.modelName)
        assertEquals("gemini-4-hydrogen-medium", archived.modelCliId)
        assertEquals("Medium", archived.thinkingBudget)
    }
}
