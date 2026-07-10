package org.munika.assignment

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Font
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowStateListener
import java.io.File
import java.io.FilenameFilter
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JFrame
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
import org.munika.assignment.ui.screen.WorkbookDropFeedback
import org.munika.assignment.ui.viewmodel.MainViewModel

private val UriListFlavor = DataFlavor("text/uri-list;class=java.lang.String")

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
    var workbookDropFeedback by remember { mutableStateOf<WorkbookDropFeedback?>(null) }

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
    val restoredWindowState = remember { dependencies.windowStateStore.load() }
    val windowBoundsTracker = remember { WindowBoundsTracker(restoredWindowState) }
    var mainWindow by remember { mutableStateOf<JFrame?>(null) }
    val windowState = rememberWindowState(
        placement = if (restoredWindowState.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        position = restoredWindowState.normalLocation?.let { WindowPosition(it.x.dp, it.y.dp) }
            ?: WindowPosition(Alignment.Center),
        size = DpSize(restoredWindowState.normalSize.width.dp, restoredWindowState.normalSize.height.dp),
    )
    Window(
        state = windowState,
        onCloseRequest = {
            mainWindow?.let { window ->
                windowBoundsTracker.capture(window)
                dependencies.windowStateStore.save(windowBoundsTracker.toAppWindowState(window))
            }
            viewModel.close()
            exitApplication()
        },
        title = "MunAssign",
    ) {
        LaunchedEffect(Unit) {
            mainWindowComposed = true
        }

        DisposableEffect(window) {
            mainWindow = window
            window.minimumSize = MinimumMainWindowSize
            windowBoundsTracker.capture(window)
            val windowBoundsListener = WindowBoundsListener(window, windowBoundsTracker)
            window.addComponentListener(windowBoundsListener)
            window.addWindowStateListener(windowBoundsListener)

            val dropTargets = window.workbookDropTargetComponents()
            val previousDropTargets = dropTargets.associateWith { it.dropTarget }
            dropTargets.forEach { component ->
                component.dropTarget = createWorkbookDropTarget(
                    viewModel = viewModel,
                    onFeedback = { workbookDropFeedback = it },
                )
            }
            onDispose {
                windowBoundsTracker.capture(window)
                window.removeComponentListener(windowBoundsListener)
                window.removeWindowStateListener(windowBoundsListener)
                if (mainWindow === window) {
                    mainWindow = null
                }
                workbookDropFeedback = null
                previousDropTargets.forEach { (component, dropTarget) ->
                    component.dropTarget = dropTarget
                }
            }
        }

        App(
            viewModel = viewModel,
            workbookDropFeedback = workbookDropFeedback,
            onOpenWorkbook = {
                val dialog = FileDialog(window, "Select update file", FileDialog.LOAD)
                dialog.filenameFilter = FilenameFilter { _, name -> name.hasWorkbookUpdateExtension() }
                dialog.isVisible = true
                if (dialog.file != null) {
                    when (
                        val selection = selectWorkbookUpdateFile(
                            files = listOf(File(dialog.directory, dialog.file)),
                            hasSelectedConference = viewModel.state.value.selectedConferenceId != null,
                            loading = viewModel.state.value.loading,
                        )
                    ) {
                        is WorkbookUpdateFileSelection.Accepted -> viewModel.updateFromWorkbook(selection.file.toPath())
                        is WorkbookUpdateFileSelection.Rejected -> viewModel.rejectWorkbookUpdate(selection.message)
                    }
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

private val MinimumMainWindowSize = Dimension(1100, 720)

private class WindowBoundsTracker(initialState: AppWindowState) {
    private var normalWindowState: AppWindowState = initialState.copy(maximized = false)

    fun capture(window: JFrame) {
        if (!window.isMaximized() && window.bounds.isValidNormalWindowBounds()) {
            normalWindowState = normalWindowState.withNormalBounds(window.bounds)
        }
    }

    fun toAppWindowState(window: JFrame): AppWindowState =
        normalWindowState.copy(maximized = window.isMaximized())
}

private class WindowBoundsListener(
    private val window: JFrame,
    private val tracker: WindowBoundsTracker,
) : ComponentAdapter(), WindowStateListener {
    override fun componentMoved(event: ComponentEvent) {
        tracker.capture(window)
    }

    override fun componentResized(event: ComponentEvent) {
        tracker.capture(window)
    }

    override fun windowStateChanged(event: WindowEvent) {
        tracker.capture(window)
    }
}

private fun JFrame.isMaximized(): Boolean =
    extendedState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH

private fun createWorkbookDropTarget(
    viewModel: MainViewModel,
    onFeedback: (WorkbookDropFeedback?) -> Unit,
): DropTarget =
    object : DropTarget() {
        override fun dragEnter(event: DropTargetDragEvent) {
            updateWorkbookDropFeedback(event, viewModel, onFeedback)
        }

        override fun dragOver(event: DropTargetDragEvent) {
            updateWorkbookDropFeedback(event, viewModel, onFeedback)
        }

        override fun dragExit(event: DropTargetEvent) {
            onFeedback(null)
        }

        override fun drop(event: DropTargetDropEvent) {
            onFeedback(null)
            event.acceptDrop(DnDConstants.ACTION_COPY)
            val files = event.workbookDropFiles()
            val selection = selectWorkbookUpdateFile(
                files = files,
                hasSelectedConference = viewModel.state.value.selectedConferenceId != null,
                loading = viewModel.state.value.loading,
            )
            when (selection) {
                is WorkbookUpdateFileSelection.Accepted -> viewModel.updateFromWorkbook(selection.file.toPath())
                is WorkbookUpdateFileSelection.Rejected -> viewModel.rejectWorkbookUpdate(selection.message)
            }
            event.dropComplete(true)
        }
    }

private fun JFrame.workbookDropTargetComponents(): List<Component> =
    listOf(this, rootPane, layeredPane, contentPane, glassPane)
        .flatMap { component -> component.withDescendants() }
        .distinct()

private fun Component.withDescendants(): List<Component> {
    if (this !is Container) {
        return listOf(this)
    }
    return listOf(this) + components.flatMap { it.withDescendants() }
}

private fun updateWorkbookDropFeedback(
    event: DropTargetDragEvent,
    viewModel: MainViewModel,
    onFeedback: (WorkbookDropFeedback?) -> Unit,
) {
    val files = event.workbookDragFiles()
    val selection = selectWorkbookUpdateFile(
        files = files,
        hasSelectedConference = viewModel.state.value.selectedConferenceId != null,
        loading = viewModel.state.value.loading,
    )
    val feedback = when (selection) {
        is WorkbookUpdateFileSelection.Accepted -> WorkbookDropFeedback.Accepted
        is WorkbookUpdateFileSelection.Rejected -> WorkbookDropFeedback.Rejected
    }
    onFeedback(feedback)
    when (selection) {
        is WorkbookUpdateFileSelection.Accepted -> event.acceptDrag(DnDConstants.ACTION_COPY)
        is WorkbookUpdateFileSelection.Rejected -> event.rejectDrag()
    }
}

private fun DropTargetDragEvent.workbookDragFiles(): List<File> {
    return transferable.fileListOrEmpty()
}

private fun DropTargetDropEvent.workbookDropFiles(): List<File> {
    return transferable.fileListOrEmpty()
}

private fun java.awt.datatransfer.Transferable.fileListOrEmpty(): List<File> =
    when {
        isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> javaFileListOrEmpty()
        isDataFlavorSupported(UriListFlavor) -> textFlavorFilesOrEmpty(UriListFlavor)
        isDataFlavorSupported(DataFlavor.stringFlavor) -> textFlavorFilesOrEmpty(DataFlavor.stringFlavor)
        else -> emptyList()
    }

private fun java.awt.datatransfer.Transferable.javaFileListOrEmpty(): List<File> =
    runCatching {
        @Suppress("UNCHECKED_CAST")
        getTransferData(DataFlavor.javaFileListFlavor) as? List<File> ?: emptyList()
    }.getOrDefault(emptyList())

private fun java.awt.datatransfer.Transferable.textFlavorFilesOrEmpty(flavor: DataFlavor): List<File> =
    runCatching {
        (getTransferData(flavor) as? String).orEmpty().toDroppedFiles()
    }.getOrDefault(emptyList())

private fun String.toDroppedFiles(): List<File> =
    lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { it.toDroppedFileOrNull() }
        .toList()

private fun String.toDroppedFileOrNull(): File? {
    runCatching {
        val uri = URI(this)
        if (uri.scheme.equals("file", ignoreCase = true)) {
            return File(uri)
        }
    }
    if (startsWith("file:", ignoreCase = true)) {
        return null
    }
    return File(this)
}

private fun startDependencies(): MainDependencies {
    startKoin { modules(appModule) }
    return MainDependencies(
        viewModel = getKoin().get(),
        appDataPaths = getKoin().get(),
        windowStateStore = getKoin().get(),
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
    val windowStateStore: AppWindowStateStore,
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
