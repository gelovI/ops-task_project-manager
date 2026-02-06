package com.ops.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SimpleVmFactory<T : ViewModel>(
    private val create: () -> T
) : ViewModelProvider.Factory {
    override fun <R : ViewModel> create(modelClass: Class<R>): R {
        @Suppress("UNCHECKED_CAST")
        return create() as R
    }
}
