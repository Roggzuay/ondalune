package io.github.roggzuay.ondalune.ui.viewmodel

import android.app.Application
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.github.roggzuay.ondalune.OndaluneApp

/**
 * The one player (the ViewModel that owns the queue and drives the engine), shared by the phone's
 * screen and Android Auto so that either works without the other. It lives while anyone holds it
 * and is cleared with its last holder, as it was when the screen alone owned it.
 */
class SharedPlayer(private val app: Application) {
    private val store = ViewModelStore()
    private var holders = 0

    @MainThread
    fun acquire(): OndaluneViewModel {
        holders++
        return ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[OndaluneViewModel::class.java]
    }

    @MainThread
    fun release() {
        if (--holders == 0) store.clear()
    }
}

/** The screen's hold on the shared player: kept across rotation, given back when the screen is gone for good. */
class PlayerLease(application: Application) : AndroidViewModel(application) {
    private val shared = (application as OndaluneApp).player
    val player: OndaluneViewModel = shared.acquire()

    override fun onCleared() = shared.release()
}
