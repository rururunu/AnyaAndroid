package ai.anya.companion.feature.chat

import ai.anya.companion.core.model.session.ToolActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadCodeCardTest {
    @Test fun readsOnlyAContinuousSuccessfulWindow() {
        val activity = ToolActivity(
            id = "read-1",
            toolName = "read_file",
            title = "Read code",
            kind = "tool",
            status = "done",
            result = "<path>src/main.kt</path>\n<content>\n10: fun main() {\n11:     println(1)\n</content>",
        )
        assertEquals(ReadCodeSnapshot("src/main.kt", 10, 11, "fun main() {\n    println(1)"), readCodeSnapshot(activity))
        assertNull(readCodeSnapshot(activity.copy(success = false)))
        assertNull(readCodeSnapshot(activity.copy(result = "<path>src/main.kt</path>\n<content>\n10: a\n12: b\n</content>")))
    }
}
