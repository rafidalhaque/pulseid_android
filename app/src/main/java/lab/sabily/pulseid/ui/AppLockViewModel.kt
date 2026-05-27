package lab.sabily.pulseid.ui

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import lab.sabily.pulseid.data.SecurityManager

class AppLockViewModel(private val securityManager: SecurityManager) : ViewModel(), DefaultLifecycleObserver {
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var backgroundTimestamp: Long = 0

    override fun onStop(owner: LifecycleOwner) {
        if (securityManager.isAppLockEnabled()) {
            backgroundTimestamp = System.currentTimeMillis()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        if (securityManager.isAppLockEnabled() && backgroundTimestamp != 0L) {
            val elapsed = System.currentTimeMillis() - backgroundTimestamp
            if (elapsed >= securityManager.getAppLockInterval()) {
                _isLocked.value = true
            }
            backgroundTimestamp = 0
        }
    }

    fun lock() {
        if (securityManager.isAppLockEnabled()) {
            _isLocked.value = true
        }
    }

    fun unlock() {
        _isLocked.value = false
    }

    class Factory(private val securityManager: SecurityManager) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppLockViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AppLockViewModel(securityManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}