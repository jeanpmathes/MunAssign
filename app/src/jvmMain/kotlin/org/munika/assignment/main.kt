package org.munika.assignment

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JOptionPane
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin
import org.munika.assignment.di.appModule
import org.munika.assignment.ui.viewmodel.MainViewModel

fun main() = application {
    startKoin { modules(appModule) }
    val viewModel = getKoin().get<MainViewModel>()
    val appDataPaths = getKoin().get<AppDataPaths>()

    Window(
        onCloseRequest = ::exitApplication,
        title = "MunAssign",
    ) {
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
                viewModel.close()
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
            },
        )
    }
}

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
