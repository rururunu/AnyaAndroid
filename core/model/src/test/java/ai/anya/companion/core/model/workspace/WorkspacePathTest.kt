package ai.anya.companion.core.model.workspace

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkspacePathTest {

    @Test
    fun downloadPathCandidates_prefersRelativeUnderWorkspaceRoot() {
        val candidates = downloadPathCandidates(
            path = """C:\My\code\DshCompanion\DshCompanion.sln""",
            rootPath = """C:\My\code\DshCompanion""",
        )
        assertThat(candidates.first()).isEqualTo("DshCompanion.sln")
        assertThat(candidates).contains("""C:\My\code\DshCompanion\DshCompanion.sln""")
        assertThat(candidates).contains("C:/My/code/DshCompanion/DshCompanion.sln")
    }

    @Test
    fun downloadPathCandidates_keepsRelativePath() {
        assertThat(downloadPathCandidates("src/Main.kt", rootPath = """C:\My\code\App"""))
            .containsExactly("src/Main.kt")
    }

    @Test
    fun normalizeSharedFilePath_collapsesDoubledSeparators() {
        assertThat(normalizeSharedFilePath("""C:\\My\\code\\foo.txt"""))
            .isEqualTo("""C:\My\code\foo.txt""")
    }
}
