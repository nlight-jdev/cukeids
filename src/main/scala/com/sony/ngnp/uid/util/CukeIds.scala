package com.sony.ngnp.uid.util

import org.rogach.scallop.LazyScallopConf
import java.io.File
import scala.io.Source
import scala.collection.JavaConverters._
import RegexUtils._
import scala.Array.canBuildFrom
import org.apache.commons.io.FileUtils
import org.rogach.scallop.ScallopConf
import java.util.Properties
import java.io.FileInputStream
import java.io.FileReader
import org.rogach.scallop.Scallop
import java.io.PrintWriter
import scala.io.Codec
import com.google.gson.Gson
import java.io.FileWriter
import CukeIds.ScenarioProcessor._
import scala.collection.mutable.MutableList
import scala.collection.mutable.ListBuffer

object CukeIds {
  var scenarioProcessor: ScenarioProcessor = _

  def main(args: Array[String]) {
    val conf = new Conf().fromFile.fromRuntimeArgs(args)

    scenarioProcessor = new ScenarioProcessor(conf.featuresPath)
    updateIndex(conf)
    updateCukes(conf)
  }

  def updateIndex(conf: Conf) {
    if (conf.updateIndex) {
      scenarioProcessor.run { ctx =>
        if (ctx.scenario.id.isDefined) conf.index = math.max(conf.index, ctx.scenario.id.get.toInt)
      }
    }
  }

  def updateCukes(conf: Conf) {
    val updateIds = new UpdateScenarioIdsAction
    scenarioProcessor.addAction(updateIds)
    scenarioProcessor.run { ctx =>
      if (!ctx.scenario.id.isDefined) {
        conf.index += 1
        updateIds.setScenarioNewId(ctx.scenario, conf.index)
      }
    }
  }

  object ScenarioProcessor {
    val ScenarioRgx = "\\s*(Scenario|Scenario Outline)\\s*:\\s*(S[0-9]+)?(.*)".r
    val ScenarioIdRgx = ".*S([0-9]+).*".r

    type ScenarioVisitor = (ScenarioCtx) => Unit

    trait Action {
      def execute(scenarioProcessor: ScenarioProcessor)
    }

    case class Scenario(
      var featureFile: File = null,
      var lineIdx: Int = 0,
      var scenarioType: String = null,
      var text: String = null,
      var id: Option[Int] = None)

    class UpdateScenarioIdsAction extends Action {
      val scenarioNewIds = MutableList[(Scenario, Int)]()

      def setScenarioNewId(scenario: Scenario, newId: Int) = scenarioNewIds += ((scenario, newId))

      def execute(scenarioProcessor: ScenarioProcessor) {
        val map = scenarioNewIds.map({
          case (scenario @ Scenario(featureFile, _, _, _, _), newId) => (featureFile, (scenario, newId))
        }).toList.groupBy(_._1) map { case (k, v) => (k, v.map(_._2)) }
        map foreach {
          case (featureFile, scenario2IdList) => {
            val lines = Source.fromFile(featureFile).getLines.to[ListBuffer]
            scenario2IdList foreach {
              case (s: Scenario, newId) => {
                val newIdStr = f"$newId%03d"
                val line = s"${s.scenarioType} : S$newIdStr ${s.text}"
                lines(s.lineIdx) = line
              }
            }
            FileUtils.writeLines(featureFile, lines.asJava)
          }
        }
      }
    }

    case class ScenarioCtx(scenarioProcessor: ScenarioProcessor, scenario: Scenario)
  }
  class ScenarioProcessor(folder: File) {
    import ScenarioProcessor._

    val actions = MutableList[Action]()

    private def getFeatureFiles(folder: File): List[File] = {
      val files = folder.listFiles
      val featureFiles = files.filter(_.getName().endsWith(".feature"))
      featureFiles ++ files.filter(_.isDirectory).flatMap(getFeatureFiles) toList
    }

    private def processScenario(visitor: ScenarioVisitor, ctx: ScenarioCtx) {
      visitor(ctx)
    }

    private def extractScenarioId(text: String) : Option[Int] = text match {
      case ScenarioIdRgx(id) => if (null != id) Some(id.toInt) else None
    }
    
    private def processFeature(f: File, visitor: ScenarioVisitor) {
      val lines = Source.fromFile(f).getLines
      lines.zipWithIndex.filter(ScenarioRgx matches _._1).toList foreach {
        case (line, idx) =>
          val scenario = Scenario(featureFile=f)
          val ctx = new ScenarioCtx(this, scenario)
          line match {
            case ScenarioRgx(scenarioType, id, text) => {
              scenario.lineIdx = idx
              scenario.scenarioType = scenarioType
              if (null != id) {
            	scenario.id = extractScenarioId(id)
              }
              scenario.text = text
              processScenario(visitor, ctx)
            }
          }
      }
    }

    def addAction(action: Action) = actions += action

    def run(scenarioVisitor: ScenarioVisitor) {
      val featureFiles = getFeatureFiles(folder)
      featureFiles foreach (processFeature(_, scenarioVisitor))
      actions foreach (_.execute(this))
    }
  }

  object Conf {
    case class StoredConf(cfg: Conf) {
      val index: Int = cfg.index

      def toConfig(c: Conf) = c.index = index
    }
  }
  class Conf {
    import Conf._
    val ConfigFileName = "config.json"
    val gson = new Gson
    var index: Int = _
    var updateIndex: Boolean = _
    var featuresPath: File = _

    def store = gson.toJson(StoredConf(this), new FileWriter(ConfigFileName))

    def fromRuntimeArgs(args: Array[String]) = {
      val opts = Scallop(args)
        .version("cukeIds 0.1 (c) 2014")
        .banner("""Usage: cukeIds [OPTION]... [FOLDER]
            |cukeIds updates the ids of all feature files     
            |Options:
            |""".stripMargin)
        .opt[Boolean]("updateIndex", descr = "")
        .opt[String]("featuresPath", required = true)
      this.updateIndex = opts[Boolean]("updateIndex")
      this.featuresPath = new File(opts[String]("featuresPath"))
      this
    }

    def fromFile = {
      try {
        val cfg = gson.fromJson(new FileReader(ConfigFileName), classOf[com.sony.ngnp.uid.util.CukeIds.Conf.StoredConf])
        cfg.toConfig(this)
      } catch {
        case _ => 
      }
      this
    }
  }
}