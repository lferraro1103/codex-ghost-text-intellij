package com.leandro.codexghosttext.status

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.leandro.codexghosttext.generation.ProviderId
import com.leandro.codexghosttext.provider.ProviderSelectionListener
import com.leandro.codexghosttext.provider.ProviderRouterService

/** Creates the project-local provider selector that remains present while no request is running. */
class ProviderSelectorWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = WIDGET_ID

    override fun getDisplayName(): String = "Codex Ghost Text Provider"

    override fun isAvailable(project: Project): Boolean = !project.isDisposed

    override fun createWidget(project: Project): StatusBarWidget = ProviderSelectorWidget(
        project,
        ProviderSelectorModel(project.getService(ProviderRouterService::class.java)),
    )

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun isEnabledByDefault(): Boolean = true

    companion object {
        const val WIDGET_ID = "CodexGhostText.ProviderSelector"
    }
}

/** Closed two-provider presentation logic, intentionally without availability probing or generation. */
internal class ProviderSelectorModel(private val router: ProviderRouterService) {
    fun choiceLabels(): List<String> = ProviderId.entries.map(::displayName)

    fun selectedValue(): String = displayName(router.snapshot().providerId)

    fun choices(): List<ProviderId> = ProviderId.entries.toList()

    fun choose(providerId: ProviderId): Boolean = router.select(providerId)

    fun displayName(providerId: ProviderId): String = when (providerId) {
        ProviderId.CODEX -> "Codex"
        ProviderId.CLAUDE -> "Claude"
    }
}

private class ProviderSelectorWidget(
    private val project: Project,
    private val model: ProviderSelectorModel,
) : StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation {
    private var statusBar: StatusBar? = null

    override fun ID(): String = ProviderSelectorWidgetFactory.WIDGET_ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getSelectedValue(): String = model.selectedValue()

    override fun getMaxValue(): String = "Claude"

    override fun getTooltipText(): String = "Proveedor de generación de código"

    override fun getPopupStep(): ListPopup = JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<ProviderId>(null, model.choices()) {
        override fun getTextFor(value: ProviderId): String = model.displayName(value)

        // `FINAL_CHOICE` is IntelliJ's Java null sentinel: it means "close this popup".
        // Keep the nullable return type so Kotlin does not turn that sentinel into an NPE
        // on IDE builds whose annotations expose this method as nullable.
        override fun onChosen(selectedValue: ProviderId, finalChoice: Boolean): PopupStep<*>? {
            model.choose(selectedValue)
            updatePresentation()
            return FINAL_CHOICE
        }
    })

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        project.messageBus.connect(this).subscribe(ProviderRouterService.SELECTION_TOPIC, ProviderSelectionListener {
            updatePresentation()
        })
    }

    override fun dispose() {
        statusBar = null
    }

    private fun updatePresentation() {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) statusBar?.updateWidget(ID())
        }
    }
}
