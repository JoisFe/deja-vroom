package ai.dejavroom.app.data

import ai.dejavroom.app.model.GeoCoordinate
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The car's position from the Pleos built-in navigation, through NaviHelper 3.0.5:
 * `getCurrentLocationInfo()` is answered on `NaviHelperEventListener.onCurrentLocationInfo`.
 * It needs `pleos.car.permission.NAVI_ROUTE` in the manifest.
 *
 * The connection is made on first use and kept until [release]. Whatever goes wrong (no navigation app
 * on this device, no answer in time, permission refused, an answer that is not a position) comes back as
 * [CurrentLocation.Unavailable]. The position itself is never logged.
 */
class PleosCurrentLocationRepository(
    context: Context,
    private val connectTimeout: Duration = 5.seconds,
    private val answerTimeout: Duration = 3.seconds,
) : CurrentLocationRepository {
    private val appContext = context.applicationContext
    private val oneAtATime = Mutex()
    private var connection: Connection? = null

    override suspend fun current(): CurrentLocation = withContext(Dispatchers.Main.immediate) {
        oneAtATime.withLock {
            try {
                val link = connection ?: connect()?.also { connection = it }
                    ?: return@withLock CurrentLocation.Unavailable(CurrentLocation.Reason.NO_NAVIGATION)
                val answer = link.ask(answerTimeout)
                if (answer == null) {
                    // A connection that stopped answering is dropped, so the next question connects again.
                    disconnect()
                    CurrentLocation.Unavailable(CurrentLocation.Reason.NO_ANSWER)
                } else {
                    answer.toCurrentLocation()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (denied: SecurityException) {
                Log.w(TAG, "NaviHelper 권한이 없다: ${denied.javaClass.simpleName}")
                CurrentLocation.Unavailable(CurrentLocation.Reason.NO_PERMISSION)
            } catch (failure: Exception) {
                Log.w(TAG, "NaviHelper에서 현재 위치를 받지 못했다: ${failure.javaClass.simpleName}")
                disconnect()
                CurrentLocation.Unavailable(CurrentLocation.Reason.NO_NAVIGATION)
            }
        }
    }

    override fun release() = disconnect()

    private fun disconnect() {
        connection?.close()
        connection = null
    }

    /** Null when the navigation service does not connect in time or reports that it cannot. */
    private suspend fun connect(): Connection? {
        val helper = NaviHelper(appContext)
        val connected = CompletableDeferred<Boolean>()
        helper.initialize(
            { connected.complete(true) },
            { failure ->
                Log.w(TAG, "NaviHelper 연결 실패: ${failure.javaClass.simpleName}")
                connected.complete(false)
            },
        )
        if (withTimeoutOrNull(connectTimeout) { connected.await() } == true) return Connection(helper)
        runCatching { helper.release() }
        return null
    }

    /** One connected helper with one listener that stays registered for as long as the connection lives. */
    private class Connection(private val helper: NaviHelper) {
        @Volatile private var pending: CompletableDeferred<CurrentLocationInfo>? = null

        private val listener = object : NaviHelperEventListener {
            override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
                pending?.complete(currentLocationInfo)
            }
        }

        init { helper.addListener(listener) }

        suspend fun ask(timeout: Duration): CurrentLocationInfo? {
            val answer = CompletableDeferred<CurrentLocationInfo>().also { pending = it }
            helper.getCurrentLocationInfo()
            return withTimeoutOrNull(timeout) { answer.await() }
        }

        fun close() {
            runCatching { helper.removeListener(listener) }
            runCatching { helper.release() }
        }
    }

    private companion object { const val TAG = "DejaVroomLocation" }
}

/**
 * Checks what the navigation said before the app trusts it. Latitude and longitude outside their ranges
 * are refused, and so is exactly (0, 0): no car this app serves is there, a navigation without a fix is.
 */
internal fun CurrentLocationInfo.toCurrentLocation(): CurrentLocation {
    val coordinate = runCatching { GeoCoordinate(latitude, longitude) }.getOrNull()
        ?.takeUnless { it.latitude == 0.0 && it.longitude == 0.0 }
        ?: return CurrentLocation.Unavailable(CurrentLocation.Reason.NO_POSITION)
    return CurrentLocation.Found(coordinate, address.trim())
}
