package com.gallopkeyboard.ime

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import timber.log.Timber

/**
 * Abstract base class that makes InputMethodService lifecycle-aware for Compose.
 *
 * InputMethodService does NOT extend ComponentActivity, so it lacks the
 * lifecycle/viewmodel/savedstate wiring that Compose expects. This class
 * manually implements the three "owner" interfaces and attaches them to the
 * ComposeView (and decorView) so Compose can find them via ViewTree lookups.
 */
abstract class LifecycleInputMethodService : InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        // savedstate 1.2+ requires attach while INITIALIZED, before restore.
        // Skipping attach crashes the IME process on first show ("nothing pops up").
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        ensureLifecycleAtLeast(Lifecycle.State.STARTED)

        val view = ComposeView(this).apply {
            // Owners MUST be on the ComposeView before setContent, not only on decorView.
            setViewTreeLifecycleOwner(this@LifecycleInputMethodService)
            setViewTreeViewModelStoreOwner(this@LifecycleInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@LifecycleInputMethodService)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle)
            )
            setContent { KeyboardContent() }
        }

        window?.window?.let { imeWindow ->
            imeWindow.decorView.setViewTreeLifecycleOwner(this@LifecycleInputMethodService)
            imeWindow.decorView.setViewTreeViewModelStoreOwner(this@LifecycleInputMethodService)
            imeWindow.decorView.setViewTreeSavedStateRegistryOwner(this@LifecycleInputMethodService)
            // Opaque fallback so a failed composition is still visible as a panel,
            // not an invisible "nothing happened" keyboard window.
            imeWindow.setBackgroundDrawable(ColorDrawable(Color.parseColor("#1C1C1E")))
        }

        Timber.d("IME input view created (lifecycle=%s)", lifecycle.currentState)
        return view
    }

    /**
     * Subclasses override this to provide the keyboard Compose UI.
     */
    @Composable
    abstract fun KeyboardContent()

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        ensureLifecycleAtLeast(Lifecycle.State.RESUMED)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // RESUMED -> STARTED. Do not emit ON_STOP here: the input view may be
        // shown again without recreating the ComposeView.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
    }

    override fun onDestroy() {
        // Wind down with legal transitions only. ON_DESTROY is only valid from CREATED;
        // jumping from RESUMED/STARTED throws and can kill the IME process on teardown.
        runCatching { moveLifecycleToDestroyed() }
            .onFailure { Timber.e(it, "IME lifecycle teardown failed") }
        store.clear()
        super.onDestroy()
    }

    /**
     * Advance lifecycle only forward. [onCreateInputView] can be called again while
     * already STARTED; blindly emitting ON_START would crash LifecycleRegistry.
     */
    private fun ensureLifecycleAtLeast(target: Lifecycle.State) {
        while (lifecycle.currentState < target &&
            lifecycle.currentState != Lifecycle.State.DESTROYED
        ) {
            val event = when (lifecycle.currentState) {
                Lifecycle.State.INITIALIZED -> Lifecycle.Event.ON_CREATE
                Lifecycle.State.CREATED -> Lifecycle.Event.ON_START
                Lifecycle.State.STARTED -> Lifecycle.Event.ON_RESUME
                else -> return
            }
            lifecycleRegistry.handleLifecycleEvent(event)
        }
    }

    private fun moveLifecycleToDestroyed() {
        while (lifecycle.currentState != Lifecycle.State.DESTROYED &&
            lifecycle.currentState != Lifecycle.State.INITIALIZED
        ) {
            val event = when {
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ->
                    Lifecycle.Event.ON_PAUSE
                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ->
                    Lifecycle.Event.ON_STOP
                lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED) ->
                    Lifecycle.Event.ON_DESTROY
                else -> return
            }
            lifecycleRegistry.handleLifecycleEvent(event)
        }
    }
}
