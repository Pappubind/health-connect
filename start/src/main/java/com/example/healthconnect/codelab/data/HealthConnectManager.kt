package com.example.healthconnect.codelab.data

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.mutableStateOf
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_AVAILABLE
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.feature.ExperimentalFeatureAvailabilityApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.healthconnect.codelab.workers.ReadStepWorker
import java.io.IOException
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.TimeUnit

// The minimum android level that can use Health Connect
const val MIN_SUPPORTED_SDK = Build.VERSION_CODES.O_MR1

/**
 * Demonstrates reading and writing from Health Connect.
 */
class HealthConnectManager(private val context: Context) {
  // HealthConnectClient instance is created lazily
  private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

  var availability = mutableStateOf(HealthConnectAvailability.NOT_SUPPORTED)
    private set

  init {
    checkAvailability()
  }

  // Checks whether Health Connect is available or not
  fun checkAvailability() {
    availability.value = when {
      HealthConnectClient.getSdkStatus(context) == SDK_AVAILABLE -> HealthConnectAvailability.INSTALLED
      isSupported() -> HealthConnectAvailability.NOT_INSTALLED
      else -> HealthConnectAvailability.NOT_SUPPORTED
    }
  }

  @OptIn(ExperimentalFeatureAvailabilityApi::class)
  // Checks if a specific feature is available in Health Connect
  fun isFeatureAvailable(feature: Int): Boolean{
    return healthConnectClient
      .features
      .getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
  }

  // Function to check if all required permissions are granted
  suspend fun hasAllPermissions(permissions: Set<String>): Boolean {
    // Check if all the permissions are granted
    return healthConnectClient.permissionController.getGrantedPermissions().containsAll(permissions)
  }

  // Function to request permissions activity contract
  fun requestPermissionsActivityContract(): ActivityResultContract<Set<String>, Set<String>> {
    // Create a contract to request permissions
    return PermissionController.createRequestPermissionResultContract()
  }

  /**
   * TODO: Writes [WeightRecord] to Health Connect.
   */
  suspend fun writeWeightInput(weightInput: Double) {
    val time = ZonedDateTime.now().withNano(0)
    val weightRecord = WeightRecord(
      weight = Mass.kilograms(weightInput),
      time = time.toInstant(),
      zoneOffset = time.offset
    )
    val records = listOf(weightRecord)
    try {
      healthConnectClient.insertRecords(records)
      Toast.makeText(context, "Successfully insert records", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
      Toast.makeText(context, e.message.toString(), Toast.LENGTH_SHORT).show()
    }
  }

  /**
   * TODO: Reads in existing [WeightRecord]s.
   */
  suspend fun readWeightInputs(start: Instant, end: Instant): List<WeightRecord> {
    val request = ReadRecordsRequest(
      recordType = WeightRecord::class,
      timeRangeFilter = TimeRangeFilter.between(start, end)
    )
    val response = healthConnectClient.readRecords(request)
    return response.records
  }

  // TODO: Returns the weekly average of [WeightRecord]s.
  suspend fun computeWeeklyAverage(start: Instant, end: Instant): Mass? {
    // This is where you can compute the weekly average weight
    return null
  }

  /**
   * TODO: Obtains a list of [ExerciseSessionRecord]s in a specified time frame.
   */
  suspend fun readExerciseSessions(start: Instant, end: Instant): List<ExerciseSessionRecord> {
    val request = ReadRecordsRequest(
      recordType = ExerciseSessionRecord::class,
      timeRangeFilter = TimeRangeFilter.between(start, end)
    )
    val response = healthConnectClient.readRecords(request)
    return response.records
  }

  /**
   * TODO: Writes an [ExerciseSessionRecord] to Health Connect.
   */
  suspend fun writeExerciseSession(start: ZonedDateTime, end: ZonedDateTime) {
    healthConnectClient.insertRecords(
      listOf(
        ExerciseSessionRecord(
          startTime = start.toInstant(),
          startZoneOffset = start.offset,
          endTime = end.toInstant(),
          endZoneOffset = end.offset,
          exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
          title = "My Run #${Random.nextInt(0, 60)}"
        ),
        StepsRecord(
          startTime = start.toInstant(),
          startZoneOffset = start.offset,
          endTime = end.toInstant(),
          endZoneOffset = end.offset,
          count = (1000 + 1000 * Random.nextInt(3)).toLong()
        ),
        TotalCaloriesBurnedRecord(
          startTime = start.toInstant(),
          startZoneOffset = start.offset,
          endTime = end.toInstant(),
          endZoneOffset = end.offset,
          energy = Energy.calories((140 + Random.nextInt(20)) * 0.01)
        )
      ) + buildHeartRateSeries(start, end)
    )
  }

  /**
   * TODO: Build [HeartRateRecord].
   */
  private fun buildHeartRateSeries(
    sessionStartTime: ZonedDateTime,
    sessionEndTime: ZonedDateTime,
  ): HeartRateRecord {
    val samples = mutableListOf<HeartRateRecord.Sample>()
    var time = sessionStartTime
    while (time.isBefore(sessionEndTime)) {
      samples.add(
        HeartRateRecord.Sample(
          time = time.toInstant(),
          beatsPerMinute = (80 + Random.nextInt(80)).toLong()
        )
      )
      time = time.plusSeconds(30)
    }
    return HeartRateRecord(
      startTime = sessionStartTime.toInstant(),
      startZoneOffset = sessionStartTime.offset,
      endTime = sessionEndTime.toInstant(),
      endZoneOffset = sessionEndTime.offset,
      samples = samples
    )
  }

  // TODO: Reads aggregated data and raw data for selected data types, for a given [ExerciseSessionRecord].
  suspend fun readAssociatedSessionData(
    uid: String,
  ): ExerciseSessionData {
    // This is where you can read associated session data
    TODO()
  }

  // TODO: Obtains a changes token for the specified record types.
  suspend fun getChangesToken(): String {
    // This is where you can get a changes token
    Toast.makeText(context, "TODO: get changes token", Toast.LENGTH_SHORT).show()
    return String()
  }

  // TODO: Retrieve changes from a changes token.
  suspend fun getChanges(token: String): Flow<ChangesMessage> = flow {
    // This is where you can retrieve changes from a changes token
    Toast.makeText(context, "TODO: get new changes", Toast.LENGTH_SHORT).show()
  }

  // Enqueue the ReadStepWorker
  fun enqueueReadStepWorker() {
    val readRequest = OneTimeWorkRequestBuilder<ReadStepWorker>()
      .setInitialDelay(10, TimeUnit.SECONDS)
      .build()
    WorkManager.getInstance(context).enqueue(readRequest)
  }

  // Convenience function to reuse code for reading data.
  private suspend inline fun <reified T : Record> readData(
    timeRangeFilter: TimeRangeFilter,
    dataOriginFilter: Set<DataOrigin> = setOf(),
  ): List<T> {
    val request = ReadRecordsRequest(
      recordType = T::class,
      dataOriginFilter = dataOriginFilter,
      timeRangeFilter = timeRangeFilter
    )
    return healthConnectClient.readRecords(request).records
  }

  // Checks if the device supports Health Connect based on Android version
  private fun isSupported() = Build.VERSION.SDK_INT >= MIN_SUPPORTED_SDK

  // Represents the two types of messages that can be sent in a Changes flow.
  sealed class ChangesMessage {
    data class NoMoreChanges(val nextChangesToken: String) : ChangesMessage()
    data class ChangeList(val changes: List<Change>) : ChangesMessage()
  }
}

// Health Connect availability status
enum class HealthConnectAvailability {
  INSTALLED,
  NOT_INSTALLED,
  NOT_SUPPORTED
}
