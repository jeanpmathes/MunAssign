package org.munika.assignment

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.FileDialog
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin
import org.munika.assignment.di.appModule
import org.munika.assignment.ui.viewmodel.MainViewModel

fun main() = application {
    startKoin { modules(appModule) }
    val viewModel = getKoin().get<MainViewModel>()

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

        App(viewModel) {
            val dialog = FileDialog(window, "Open MyMUN workbook", FileDialog.LOAD)
            dialog.isVisible = true
            if (dialog.file != null) {
                viewModel.updateFromWorkbook(File(dialog.directory, dialog.file).toPath())
            }
        }
    }
}
