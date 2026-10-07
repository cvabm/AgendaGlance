package com.billremind.app.calendar

import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Cancels the provider query immediately when the caller stops or starts another read. */
suspend fun <T> cancellableCalendarQuery(block: (CancellationSignal) -> T): T =
    withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            continuation.resumeWith(runCatching {
                signal.throwIfCanceled()
                block(signal)
            })
        }
    }
