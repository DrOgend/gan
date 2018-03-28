package GAN

//import scala.concurrent.{Future, Await}
//import scala.concurrent.duration._
//import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.util.{ Success, Failure }
import ExecutionContext.Implicits.global
import com.paulgoldbaum.influxdbclient._
import java.time._
import Console._
import java.time.format.DateTimeFormatter
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.ArrayBuffer
//import jawn.ast._
//import jawn.ast
//import jawn.AsyncParser
//import jawn.ParseException
import scala.collection.mutable.ArrayBuffer
import java.io._
import scala.io
import scala.util.control.Exception._

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging._

object influxdbGatlingANalyzer {
  class ResponseTimeRecord(var Request: String = "", var Status: String = "", var Count: BigDecimal = 0, var Perc90: BigDecimal = 0, var Perc50: BigDecimal = 0, var StdDev: BigDecimal = 0)

  implicit def Str2BD(s: Any): Option[BigDecimal] = {
    try {
      Some(Option(s.asInstanceOf[BigDecimal]).getOrElse(0))
    } catch {
      case e: NumberFormatException => None
    }
  }

  implicit def Str2BD(s: String): Option[BigDecimal] = {
    try {
      Some(BigDecimal(Option(s).getOrElse(0).asInstanceOf[String]))
    } catch {
      case e: NumberFormatException => None
    }
  }

  private val logger = Logger(LoggerFactory.getLogger(this.getClass))
  private var StackTrace = new StringWriter

  def main(args: Array[String]) {
    if (args.length < 3) {
      println(s"$RED" + "Dude, i need at least three parameters! :(")
      println(s"$BLUE" + "Example 1: java -jar gan-assembly-1.0.jar ufr-mobileregistration-basescenario 201711211758 201711211900 -  print transaction avg resp times")
      println(s"$BLUE" + "Example 2: java -jar gan-assembly-1.0.jar ufr-mobileregistration-basescenario 201711211758 201711211900 CHECK - check transaction avg times with sla.txt file, to satisfy SLA ")
      println(s"$BLUE" + "Example 3: java -jar gan-assembly-1.0.jar ufr-mobileregistration-basescenario 201711211758 201711211900 SLA - generate SLA file: sla.txt this file have to be edite with max resp times allowed to SLA")
      println(s"$BLUE" + "where ufr-mobileregistration-basescenario - test_scenario name and 201711211758 201711211900 - start and end dates")
      println(s"$BLUE" + "------------------------------------------------------------------------------------------------------------------")
      System.exit(1)
    }

    if (args.length > 4) {
      println(s"$RED" + "Dude, too much parameters :( need max only 4")
      System.exit(1)
    }

    println(s"$BLUE" + "args count: " + args.length)

    println(s"$BLUE" + "args 0: " + args(0))
    println(s"$BLUE" + "args 1: " + args(1))
    println(s"$BLUE" + "args 2: " + args(2))
    if (args.length == 4) println("args 3: " + args(3))

    //convert dates     
    //to UTC TimeZone in InfluxDB
    val uzone = ZoneId.of("Universal")
    //TZ local default
    val lzone = ZoneId.systemDefault()
    //get input Dates
    val tmin_in = LocalDateTime.of(args(1).take(4).toInt, //year
      args(1).drop(4).take(2).toInt, //month
      args(1).drop(6).take(2).toInt, //day
      args(1).drop(8).take(2).toInt, //hour
      args(1).drop(10).take(2).toInt //minutes
      ) //wait for datetime in format yyyymmddhhmi
    val tmax_in = LocalDateTime.of(args(2).take(4).toInt, //year
      args(2).drop(4).take(2).toInt, //month
      args(2).drop(6).take(2).toInt, //day
      args(2).drop(8).take(2).toInt, //hour
      args(2).drop(10).take(2).toInt //minutes
      ) //wait for datetime in format yyyymmddhhmi

    val tmin = tmin_in.atZone(lzone)
    val tmax = tmax_in.atZone(lzone)

    //DateTime format for query
    val dt_format = DateTimeFormatter.ISO_INSTANT

    val str_tmin = tmin.withZoneSameInstant(uzone).format(dt_format)
    val str_tmax = tmax.withZoneSameInstant(uzone).format(dt_format)

    //simulation
    val simulation = args(0)

    //RUN Mode flags
    var SLA_flag_gen = false
    var CHECK_flag_gen = false

    if (args.length == 4) {
      args(3) match {
        case "SLA" => {
          SLA_flag_gen = true
        }
        case "CHECK" => {
          CHECK_flag_gen = true
        }
        case _ => {
          logger.error(s"$RED" + "Wrong switch:" + args(3) + "Should use SLA or CHECK switch")
          System.exit(1)
        }
      }
    }

    //format query
    val run_query = "select sum(count) as count, percentile(percentiles90,95) as perc90, percentile(percentiles50,95) as perc50, percentile(stdDev,95) as stdDev from gatling  where  time >= '" + str_tmin + "' and time < '" + str_tmax + "' and simulation = '" + simulation + "' and (status ='ok' or status ='ko')  GROUP BY request,status"

    println(s"$BLUE" + "Run query: " + run_query)

    // connect to the database 
    val influxdb = InfluxDB.connect("172.25.198.27", 8086)
    try {
      Thread.sleep(1000)

      // ping InfluxDB
      val ping = influxdb.ping()
      ping.onComplete {
        case Success(result) =>
          {
            println(s"$BLUE" + "Influx DB + OK")
          }
        case Failure(t) => {
          println(s"$RED" + "Failed connect to Influx DB - KO " + s"$BLUE" + " ")
          logger.error(s"Failed connect to Influx DB")
          t.printStackTrace(new PrintWriter(StackTrace))
          logger.error(StackTrace.toString)
          influxdb.close()
        }
      }

      val database = influxdb.selectDatabase("gatlingdb")

      val db_ok = database.exists()
      db_ok.onComplete {
        case Success(res) => {
          println("Selected gatlingdb + OK")
        }
        case Failure(d) => {
          logger.error(s"$RED" + "Failed select gatlingdb - KO " + s"$BLUE" + " ")
          d.printStackTrace(new PrintWriter(StackTrace))
          logger.error(StackTrace.toString)
          influxdb.close()
        }
      }

      val res = database.query(run_query)

      res.onComplete {
        case Success(result) => {
          //val l_request = new ListBuffer[String]()
          //val l_simulation = new ListBuffer[String]()
          //val l_status = new ListBuffer[String]()
          //class ResponseTimeRecord(request:String,status:String,count:Long,perc90:Long,perc50:Long,stdDev:Long)

          //Arrays for response time data
          var respTimesDataSet = new ArrayBuffer[ResponseTimeRecord]()

          logger.debug(s"$BLUE" + "query run + OK")
          logger.debug(s"$BLUE" + "length: " + result.series.length)

          logger.debug(s"$YELLOW" + "______________________________________________________________________")
          logger.debug(s"$YELLOW" + "Get Response time data from InfluxDB:")
          logger.debug(s"$YELLOW" + "______________________________________________________________________")
          try {

            result.series.foreach(l_series => {
              var respTimesDataRow = new ResponseTimeRecord()
              //println(s"$YELLOW" + "TD request=" + l_series.tags("request") + " status=" + l_series.tags("status") + " count=" + l_series.records(0).apply("count") + " perc90=" + l_series.records(0).apply("perc90") + " perc50=" + l_series.records(0).apply("perc50") + " stdDev=" + l_series.records(0).apply("stdDev"))
              respTimesDataRow.Request = l_series.tags("request").toString
              respTimesDataRow.Status = l_series.tags("status").toString
              respTimesDataRow.Count = Str2BD(l_series.records(0).apply("count")).getOrElse(0)
              respTimesDataRow.Perc90 = Str2BD(l_series.records(0).apply("perc90")).getOrElse(0)
              respTimesDataRow.Perc50 = Str2BD(l_series.records(0).apply("perc50")).getOrElse(0)
              respTimesDataRow.StdDev = Str2BD(l_series.records(0).apply("stdDev")).getOrElse(0)

              logger.debug(s"$YELLOW" + "TD request=" + respTimesDataRow.Request
                + " status=" + respTimesDataRow.Status
                + " count=" + respTimesDataRow.Count
                + " perc90=" + respTimesDataRow.Perc90
                + " perc50=" + respTimesDataRow.Perc50
                + " stdDev=" + respTimesDataRow.StdDev)

              respTimesDataSet.append(respTimesDataRow)
            })

          } catch {
            case e: Throwable => {
              logger.error(s"$RED" + "Failed to parse response time data - KO" + s"$BLUE" + " ")
              e.printStackTrace(new PrintWriter(StackTrace))
              logger.error(StackTrace.toString)
            }
          }

          //respTimesDataSet.foreach(l_eq => println(l_eq.Request))

          logger.debug(s"$YELLOW" + "______________________________________________________________________")

          if (SLA_flag_gen) {
            val SLA_file = new File("sla.txt")
            val SLA_bw = new BufferedWriter(new FileWriter(SLA_file))
            try {
              println(s"$GREEN" + "______________________________________________________________________")
              println(s"$GREEN" + "Write SLA data to file: " + SLA_file.getCanonicalPath())
              println(s"$GREEN" + "______________________________________________________________________")
              respTimesDataSet.sortWith(_.Status > _.Status).sortWith(_.Request < _.Request).foreach(l_array => {
                SLA_bw.write(l_array.Request + ";" + l_array.Status + ";" + l_array.Count + ";" + l_array.Perc90 + ";" + l_array.Perc50 + ";" + l_array.StdDev + "\r" + "\n")
              })
              SLA_bw.close()
              println(s"$GREEN" + "______________________________________________________________________")
            } catch {
              case e: Throwable => {
                logger.error(s"$RED" + "Failed write SLA file - KO" + s"$BLUE" + " ")
                e.printStackTrace(new PrintWriter(StackTrace))
                logger.error(StackTrace.toString)
              }
            }

          } else if (CHECK_flag_gen) {
            try {
              var bufferedSource = io.Source.fromFile("sla.txt")
              var SLArespTimesDataSet = new ArrayBuffer[ResponseTimeRecord]()
              var PrtString = new String("")
              var Request = new String("")
              var StdDev = BigDecimal(0)
              var Perc90 = BigDecimal(0)
              var Perc50 = BigDecimal(0)
              var Count = BigDecimal(0)
              var ErrorCount = BigDecimal(0)

              var SLAStdDev = BigDecimal(0)
              var SLAPerc90 = BigDecimal(0)
              var SLAPerc50 = BigDecimal(0)
              var SLACount = BigDecimal(0)
              var SLAErrorCount = BigDecimal(0)

              var SLACurrentLine = 0

              //Load SLA data
              for (line <- bufferedSource.getLines) {
                var SLArespTimesDataRow = new ResponseTimeRecord()
                var SLAStringParamCount = line.toString.split(";").map(_.trim).size

                SLACurrentLine = SLACurrentLine + 1

                try {
                  if (SLAStringParamCount == 6) {
                    SLArespTimesDataRow.Request = line.toString.split(";").map(_.trim).apply(0)
                    SLArespTimesDataRow.Status = line.toString.split(";").map(_.trim).apply(1)
                    SLArespTimesDataRow.Count = Str2BD(line.toString.split(";").map(_.trim).apply(2)).getOrElse(0)
                    SLArespTimesDataRow.Perc90 = Str2BD(line.toString.split(";").map(_.trim).apply(3)).getOrElse(0)
                    SLArespTimesDataRow.Perc50 = Str2BD(line.toString.split(";").map(_.trim).apply(4)).getOrElse(0)
                    SLArespTimesDataRow.StdDev = Str2BD(line.toString.split(";").map(_.trim).apply(5)).getOrElse(0)
                  } else if (SLAStringParamCount == 1) {
                    println(s"$YELLOW Empty string in SLA file $BLUE" + " at line : " + SLACurrentLine)
                  } else {
                    println(s"$RED Wrong SLA file format - ko  $BLUE items count = " + SLAStringParamCount + " at line : " + SLACurrentLine)
                  }
                } catch {
                  case e: Throwable => {
                    logger.error(s"$RED" + "Error on formatting SLA file data  - KO" + s"$BLUE" + " data: " +
                      line.toString.split(";").map(_.trim).apply(0) + " "
                      + line.toString.split(";").map(_.trim).apply(1) + " "
                      + Option(line.toString.split(";").map(_.trim).apply(2)).getOrElse(0) + " "
                      + Option(line.toString.split(";").map(_.trim).apply(3)).getOrElse(0) + " "
                      + Option(line.toString.split(";").map(_.trim).apply(4)).getOrElse(0) + " "
                      + Option(line.toString.split(";").map(_.trim).apply(5)).getOrElse(0))
                    e.printStackTrace(new PrintWriter(StackTrace))
                    logger.error(StackTrace.toString)
                  }
                }

                SLArespTimesDataSet.append(SLArespTimesDataRow)
              }

              println(s"$GREEN" + "______________________________________________________________________")
              println(s"$GREEN" + "SLA data from file:")
              println(s"$GREEN" + "______________________________________________________________________")
              SLArespTimesDataSet.foreach(sla_array => {
                println(s"$GREEN" + "SLA request=" + sla_array.Request + " status=" + sla_array.Status + " count=" + sla_array.Count + " perc90=" + sla_array.Perc90 + " perc50=" + sla_array.Perc50 + " stdDev=" + sla_array.StdDev)
              })
              println(s"$GREEN" + "______________________________________________________________________")

              //Compare resp times with SLA
              println(s"$BLUE" + "_________________________________________________________________________________________________________________________________")
              println(s"$BLUE" + "| REQUEST                  |  Count ok | Count ok SLA | Perc90 ms | Perc90 SLA ms |  3* StdDev  | ERROR Count | ERROR Count SLA |")
              println(s"$BLUE" + "_________________________________________________________________________________________________________________________________")

              respTimesDataSet.groupBy(_.Request).foreach(distinct_requests => {
                var SLArespTimesDataRow2Check = new ArrayBuffer[ResponseTimeRecord]()
                Request = distinct_requests._1
                StdDev = distinct_requests._2.filter(_.Status == "ok")(0).StdDev
                Perc90 = distinct_requests._2.filter(_.Status == "ok")(0).Perc90
                Perc50 = distinct_requests._2.filter(_.Status == "ok")(0).Perc50
                Count = distinct_requests._2.filter(_.Status == "ok")(0).Count
                ErrorCount = distinct_requests._2.filter(_.Status == "ko")(0).Count

                //Check if SLA exists
                SLArespTimesDataRow2Check = SLArespTimesDataSet.filter(_.Request == distinct_requests._1)
                if (!(SLArespTimesDataRow2Check.isEmpty)) {

                  //format print String
                  //s"$BLUE" + "| REQUEST               |  Resp Time Perc90 FACT | Resp Time Perc90 SLA  | COUNT FACT    | COUNT SLA   |   ERROR COUNT %   | ERROR % SLA |"
                  PrtString = s"$BLUE| %-24s |".format(Request)

                  //Check transaction passed
                  SLACount = SLArespTimesDataRow2Check.filter(_.Status == "ok")(0).Count
                  if ((Count >= SLACount * 1.1) || (Count <= SLACount * 0.9)) {
                    PrtString += s"$RED %-9s | %-12s $BLUE|".format(Count, SLACount)

                  } else {
                    PrtString += s"$GREEN %-9s | %-12s $BLUE|".format(Count, SLACount)

                  }

                  //Check resp time - Perc 90 in interval SLA Perc90 +/- 3 StdDev
                  if (StdDev == 0) {
                    StdDev = Perc90 * 0.1
                  }
                  SLAPerc90 = SLArespTimesDataRow2Check.filter(_.Status == "ok")(0).Perc90
                  if (Perc90 >= SLAPerc90 + 3 * StdDev) {
                    PrtString += s"$RED %-9s | %-13s | %-11s $BLUE|".format(Perc90, SLAPerc90, 3 * StdDev)

                  } else if (Perc90 <= SLAPerc90 - 3 * StdDev) {
                    PrtString += s"$GREEN %-9s | %-13s | %-11s $BLUE|".format(Perc90, SLAPerc90, 3 * StdDev)

                  } else {
                    PrtString += s"$BLUE %-9s | %-13s | %-11s |".format(Perc90, SLAPerc90, 3 * StdDev)
                  }

                  //println(SLArespTimesDataRow2Check.filter(_.Status == "ko")(0))
                  //Check errors
                  if (!(SLArespTimesDataRow2Check.filter(_.Status == "ko").isEmpty)) {

                    SLAErrorCount = SLArespTimesDataRow2Check.filter(_.Status == "ko")(0).Count
                    if (SLAErrorCount == 0) {
                      SLAErrorCount = Count * 0.1
                    }

                    if (ErrorCount >= SLAErrorCount) {
                      PrtString += s"$RED %-11s | %-15s $BLUE|".format(ErrorCount, SLAErrorCount)

                    } else {
                      PrtString += s"$GREEN %-11s | %-15s $BLUE| ".format(ErrorCount, SLAErrorCount)

                    }
                  } else {
                    PrtString += s"$RED %-11s | NO SLA DATA- KO $BLUE|".format(ErrorCount)
                  }

                  println(PrtString)

                  println(s"$BLUE" + "_________________________________________________________________________________________________________________________________")

                } else {
                  PrtString = s"$RED| %-24s | %-9s | NO SLA DATA  | %-9s | NO SLA DATA   | %-11s | %-11s | NO SLA DATA     |".format(Request, Count, Perc90, 3 * StdDev, ErrorCount)
                  println(PrtString)
                  println(s"$BLUE" + "_________________________________________________________________________________________________________________________________")

                }

              })

            } catch {
              case e: java.io.FileNotFoundException => {
                logger.error(s"$RED" + "NO sla.txt file found - KO" + s"$BLUE" + " ")
                e.printStackTrace(new PrintWriter(StackTrace))
                logger.error(StackTrace.toString)
              }
              case e: Throwable => {
                logger.error(s"$RED" + "Some error on CHECK reps times 2 SLA- KO" + s"$BLUE" + " ")
                e.printStackTrace(new PrintWriter(StackTrace))
                logger.error(StackTrace.toString)
              }
            }
          }

          influxdb.close()
        }
        case Failure(t) => {
          logger.error(s"$RED" + "Failed run query - KO " + s"$BLUE" + " ")
          t.printStackTrace(new PrintWriter(StackTrace))
          logger.error(StackTrace.toString)
          influxdb.close()
        }
      }
    } catch {
      case e: Throwable =>
        e.printStackTrace(new PrintWriter(StackTrace))
        logger.error(StackTrace.toString)
    } finally {
    }

  }
}