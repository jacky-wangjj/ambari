/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ambari.metrics.adservice.db

import java.sql.{Connection, PreparedStatement, ResultSet, SQLException}
import java.util.concurrent.TimeUnit.SECONDS

import org.apache.ambari.metrics.adservice.app.AnomalyDetectionAppConfig
import org.apache.ambari.metrics.adservice.common._
import org.apache.ambari.metrics.adservice.configuration.HBaseConfiguration
import org.apache.ambari.metrics.adservice.metadata.MetricKey
import org.apache.ambari.metrics.adservice.model.AnomalyDetectionMethod.AnomalyDetectionMethod
import org.apache.ambari.metrics.adservice.model.AnomalyType.AnomalyType
import org.apache.ambari.metrics.adservice.model.{AnomalyDetectionMethod, AnomalyType, SingleMetricAnomalyInstance}
import org.apache.ambari.metrics.adservice.subsystem.pointintime.PointInTimeAnomalyInstance
import org.apache.ambari.metrics.adservice.subsystem.trend.TrendAnomalyInstance
import org.apache.hadoop.hbase.util.RetryCounterFactory
import org.apache.hadoop.metrics2.sink.timeline.query.{DefaultPhoenixDataSource, PhoenixConnectionProvider}

import com.google.inject.Inject

object PhoenixAnomalyStoreAccessor  {

  @Inject
  var configuration: AnomalyDetectionAppConfig = _

  var datasource: PhoenixConnectionProvider = _

  def initAnomalyMetricSchema(): Unit = {

    val datasource: PhoenixConnectionProvider = new DefaultPhoenixDataSource(HBaseConfiguration.getHBaseConf)
    val retryCounterFactory = new RetryCounterFactory(10, SECONDS.toMillis(3).toInt)

    val ttl = configuration.getAdServiceConfiguration.getAnomalyDataTtl
    try {
      var conn = datasource.getConnectionRetryingOnException(retryCounterFactory)
      var stmt = conn.createStatement

      val methodParametersSql = String.format(PhoenixQueryConstants.CREATE_METHOD_PARAMETERS_TABLE,
        PhoenixQueryConstants.METHOD_PARAMETERS_TABLE_NAME)
      stmt.executeUpdate(methodParametersSql)

      val pointInTimeAnomalySql = String.format(PhoenixQueryConstants.CREATE_PIT_ANOMALY_METRICS_TABLE_SQL,
        PhoenixQueryConstants.PIT_ANOMALY_METRICS_TABLE_NAME,
        ttl.asInstanceOf[Object])
      stmt.executeUpdate(pointInTimeAnomalySql)

      val trendAnomalySql = String.format(PhoenixQueryConstants.CREATE_TREND_ANOMALY_METRICS_TABLE_SQL,
        PhoenixQueryConstants.TREND_ANOMALY_METRICS_TABLE_NAME,
        ttl.asInstanceOf[Object])
      stmt.executeUpdate(trendAnomalySql)

      val snapshotSql = String.format(PhoenixQueryConstants.CREATE_MODEL_SNAPSHOT_TABLE,
        PhoenixQueryConstants.MODEL_SNAPSHOT)
      stmt.executeUpdate(snapshotSql)

      conn.commit()
    } catch {
      case e: SQLException => throw e
    }
  }

  @throws[SQLException]
  def getConnection: Connection = datasource.getConnection

  def getSingleMetricAnomalies(anomalyType: AnomalyType, startTime: Long, endTime: Long, limit: Int) : scala.collection.mutable.MutableList[SingleMetricAnomalyInstance] = {
    val anomalies = scala.collection.mutable.MutableList.empty[SingleMetricAnomalyInstance]
    val conn : Connection = getConnection
    var stmt : PreparedStatement = null
    var rs : ResultSet = null
    val s : Season = Season(Range(-1,-1), SeasonType.DAY)

    try {
      stmt = prepareAnomalyMetricsGetSqlStatement(conn, anomalyType, startTime, endTime, limit)
      rs = stmt.executeQuery
      if (anomalyType.equals(AnomalyType.POINT_IN_TIME)) {
        while (rs.next()) {
          val uuid: Array[Byte] = rs.getBytes("METRIC_UUID")
          val timestamp: Long = rs.getLong("ANOMALY_TIMESTAMP")
          val metricValue: Double = rs.getDouble("METRIC_VALUE")
          val methodType: AnomalyDetectionMethod = AnomalyDetectionMethod.withName(rs.getString("METHOD_NAME"))
          val season: Season = Season.fromJson(rs.getString("SEASONAL_INFO"))
          val anomalyScore: Double = rs.getDouble("ANOMALY_SCORE")
          val modelSnapshot: String = rs.getString("MODEL_PARAMETERS")

          val metricKey: MetricKey = null //MetricManager.getMetricKeyFromUuid(uuid) //TODO
          val anomalyInstance: SingleMetricAnomalyInstance = new PointInTimeAnomalyInstance(metricKey, timestamp,
            metricValue, methodType, anomalyScore, season, modelSnapshot)
          anomalies.+=(anomalyInstance)
        }
      } else {
        while (rs.next()) {
          val uuid: Array[Byte] = rs.getBytes("METRIC_UUID")
          val anomalyStart: Long = rs.getLong("ANOMALY_PERIOD_START")
          val anomalyEnd: Long = rs.getLong("ANOMALY_PERIOD_END")
          val referenceStart: Long = rs.getLong("TEST_PERIOD_START")
          val referenceEnd: Long = rs.getLong("TEST_PERIOD_END")
          val methodType: AnomalyDetectionMethod = AnomalyDetectionMethod.withName(rs.getString("METHOD_NAME"))
          val season: Season = Season.fromJson(rs.getString("SEASONAL_INFO"))
          val anomalyScore: Double = rs.getDouble("ANOMALY_SCORE")
          val modelSnapshot: String = rs.getString("MODEL_PARAMETERS")

          val metricKey: MetricKey = null //MetricManager.getMetricKeyFromUuid(uuid) //TODO
          val anomalyInstance: SingleMetricAnomalyInstance = TrendAnomalyInstance(metricKey,
            TimeRange(anomalyStart, anomalyEnd),
            TimeRange(referenceStart, referenceEnd),
            methodType, anomalyScore, season, modelSnapshot)
          anomalies.+=(anomalyInstance)
        }
      }
    } catch {
      case e: SQLException => throw e
    }

    anomalies
  }

  @throws[SQLException]
  def prepareAnomalyMetricsGetSqlStatement(connection: Connection, anomalyType: AnomalyType, startTime: Long, endTime: Long, limit: Int): PreparedStatement = {

    val sb = new StringBuilder

    if (anomalyType.equals(AnomalyType.POINT_IN_TIME)) {
      sb.++=(String.format(PhoenixQueryConstants.GET_PIT_ANOMALY_METRIC_SQL, PhoenixQueryConstants.PIT_ANOMALY_METRICS_TABLE_NAME))
    } else {
      sb.++=(String.format(PhoenixQueryConstants.GET_TREND_ANOMALY_METRIC_SQL, PhoenixQueryConstants.TREND_ANOMALY_METRICS_TABLE_NAME))
    }

    sb.append(" LIMIT " + limit)
    var stmt: java.sql.PreparedStatement = null
    try {
      stmt = connection.prepareStatement(sb.toString)
      var pos = 1

      pos += 1
      stmt.setLong(pos, startTime)

      stmt.setLong(pos, endTime)

      stmt.setFetchSize(limit)

    } catch {
      case e: SQLException =>
        if (stmt != null)
          stmt
        throw e
    }
    stmt
  }
}
