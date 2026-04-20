package com.chrisalvis.rotato.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisalvis.rotato.data.RotatoPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SetupStep { WELCOME }

class SetupViewModel(app: Application) : AndroidViewModel(app) {

    private val rotatoPrefs = RotatoPreferences(app)

    private val _step = MutableStateFlow(SetupStep.WELCOME)
    val step: StateFlow<SetupStep> = _step.asStateFlow()

    fun complete() {
        viewModelScope.launch {
            rotatoPrefs.setSetupDone(true)
        }
    }
}
