package it.sanninicistyle.watchsync.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide scope for short fire-and-forget work that must outlive the component that
 * started it, e.g. telling the peer about a Stop while the ringing service shuts down.
 */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)
