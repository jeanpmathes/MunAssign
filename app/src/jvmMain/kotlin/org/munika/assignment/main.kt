package org.munika.assignment

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Font
import java.awt.Frame
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JOptionPane
import javax.swing.JProgressBar
import javax.swing.JWindow
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin
import org.munika.assignment.di.appModule
import org.munika.assignment.ui.viewmodel.MainViewModel

fun main() {
    val splashWindow = ImmediateSplashWindow()
    splashWindow.show()
    val dependencies = startDependencies()

    application {
        MunAssignApplication(
            splashWindow = splashWindow,
            dependencies = dependencies,
            exitApplication = ::exitApplication,
        )
    }
}

@Composable
private fun MunAssignApplication(
    splashWindow: ImmediateSplashWindow,
    dependencies: MainDependencies,
    exitApplication: () -> Unit,
) {
    var splashMinimumVisible by remember { mutableStateOf(true) }
    var mainWindowComposed by remember { mutableStateOf(false) }
    var splashClosed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        splashMinimumVisible = false
    }

    val state by dependencies.viewModel.state.collectAsState()
    val startupLoading = state.startupLoading

    LaunchedEffect(startupLoading, splashMinimumVisible, mainWindowComposed) {
        if (!startupLoading && !splashMinimumVisible && mainWindowComposed && !splashClosed) {
            delay(150)
            splashWindow.close()
            splashClosed = true
        }
    }

    val viewModel = dependencies.viewModel
    val appDataPaths = dependencies.appDataPaths
    Window(
        onCloseRequest = {
            viewModel.close()
            exitApplication()
        },
        title = "MunAssign",
    ) {
        LaunchedEffect(Unit) {
            mainWindowComposed = true
        }

        DisposableEffect(window) {
            val dropTarget = object : DropTarget() {
                override fun drop(event: DropTargetDropEvent) {
                    event.acceptDrop(DnDConstants.ACTION_COPY)
                    val payload = event.transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor)
                    @Suppress("UNCHECKED_CAST")
                    val files = payload as? List<File>
                    files?.firstOrNull()?.toPath()?.let(viewModel::updateFromWorkbook)
                    event.dropComplete(true)
                }
            }
            window.dropTarget = dropTarget
            onDispose {
                window.dropTarget = null
            }
        }

        App(
            viewModel = viewModel,
            onOpenWorkbook = {
                val dialog = FileDialog(window, "Select update file", FileDialog.LOAD)
                dialog.isVisible = true
                if (dialog.file != null) {
                    viewModel.updateFromWorkbook(File(dialog.directory, dialog.file).toPath())
                }
            },
            onExportConference = { conferenceId, conferenceName ->
                val dialog = FileDialog(window, "Export $conferenceName", FileDialog.SAVE)
                dialog.file = "${conferenceName.fileSafeName()}.munassign"
                dialog.isVisible = true
                val selected = dialog.selectedFileWithMunAssignExtension()
                if (selected != null && (!selected.exists() || confirmOverwrite(selected))) {
                    viewModel.exportConference(conferenceId, selected.toPath())
                }
            },
            onImportConferenceAsNew = {
                openMunAssignFile(window, "Import .munassign")?.let { selected ->
                    viewModel.importConferenceAsNew(selected.toPath())
                }
            },
            onReplaceConference = { conferenceId, conferenceName ->
                openMunAssignFile(window, "Replace $conferenceName")?.let { selected ->
                    viewModel.replaceConference(conferenceId, selected.toPath())
                }
            },
            onOpenMarkdownExportFolder = {
                openMarkdownExportFolder(window, appDataPaths.markdownExportDirectory)
            }
        )
    }
}

private fun startDependencies(): MainDependencies {
    startKoin { modules(appModule) }
    return MainDependencies(
        viewModel = getKoin().get(),
        appDataPaths = getKoin().get(),
    )
}

private class ImmediateSplashWindow {
    private val window: JWindow = JWindow()

    init {
        val panel = JPanel(BorderLayout(0, 16)).apply {
            background = Color(0xFF, 0xFF, 0xFF)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0xDD, 0xE1, 0xE6)),
                BorderFactory.createEmptyBorder(32, 36, 28, 36),
            )
            preferredSize = Dimension(360, 220)
        }
        val title = JLabel("MunAssign", SwingConstants.CENTER).apply {
            font = Font(Font.SANS_SERIF, Font.BOLD, 28)
            foreground = Color(0x1D, 0x1B, 0x20)
        }
        val message = JLabel("Preparing conferences", SwingConstants.CENTER).apply {
            font = Font(Font.SANS_SERIF, Font.PLAIN, 15)
            foreground = Color(0x5F, 0x63, 0x68)
        }
        val textPanel = JPanel(BorderLayout(0, 8)).apply {
            background = panel.background
            add(title, BorderLayout.NORTH)
            add(message, BorderLayout.CENTER)
        }
        val progress = JProgressBar().apply {
            isIndeterminate = true
            isBorderPainted = false
        }

        panel.add(textPanel, BorderLayout.CENTER)
        panel.add(progress, BorderLayout.SOUTH)

        window.setContentPane(panel)
        window.isAlwaysOnTop = true
        window.setFocusableWindowState(false)
        window.setAutoRequestFocus(false)
        window.pack()
        window.setLocationRelativeTo(null)
    }

    fun show() {
        SwingUtilities.invokeLater {
            window.isVisible = true
            window.toFront()
        }
    }

    fun close() {
        SwingUtilities.invokeLater {
            window.isVisible = false
            window.dispose()
        }
    }
}

private data class MainDependencies(
    val viewModel: MainViewModel,
    val appDataPaths: AppDataPaths,
)

private fun openMarkdownExportFolder(parent: Frame, directory: Path) {
    try {
        Files.createDirectories(directory)
        Desktop.getDesktop().open(directory.toFile())
    } catch (exception: Exception) {
        JOptionPane.showMessageDialog(
            parent,
            "Could not open the textual exports folder: ${exception.message ?: exception::class.simpleName.orEmpty()}",
            "Open textual exports folder",
            JOptionPane.ERROR_MESSAGE,
        )
    }
}

private fun openMunAssignFile(parent: Frame, title: String): File? {
    val dialog = FileDialog(parent, title, FileDialog.LOAD)
    dialog.filenameFilter = FilenameFilter { _, name -> name.endsWith(".munassign", ignoreCase = true) }
    dialog.isVisible = true
    return if (dialog.file != null) File(dialog.directory, dialog.file) else null
}

private fun FileDialog.selectedFileWithMunAssignExtension(): File? {
    val selectedFile = file ?: return null
    val fileName = if (selectedFile.endsWith(".munassign", ignoreCase = true)) selectedFile else "$selectedFile.munassign"
    return File(directory, fileName)
}

private fun confirmOverwrite(file: File): Boolean =
    JOptionPane.showConfirmDialog(
        null,
        "Overwrite ${file.name}?",
        "Export file already exists",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE,
    ) == JOptionPane.YES_OPTION

private fun String.fileSafeName(): String =
    trim()
        .replace(Regex("[\\\\/:*?\"<>|]+"), "-")
        .replace(Regex("\\s+"), " ")
        .ifBlank { "conference" }
