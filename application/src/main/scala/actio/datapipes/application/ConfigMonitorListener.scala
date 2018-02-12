package actio.d.getCanonicalPathatapipes.application

import java.io.File

import actio.datapipes.pipescript.ConfigReader
import actio.datapipes.pipescript.Pipeline.Builder
import com.typesafe.scalalogging.Logger
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor
import org.quartz.Scheduler

class ConfigMonitorListener(scheduler: Scheduler) extends FileAlterationListenerAdaptor {
  lazy val logger = Logger("ConfigMonitorListener")

  override def onFileChange(file: File) {

    logger.info(s"Change detected for file: ${file.toString}")

    val config = ConfigReader.read(file)
    val pipeScript = Builder.build(file.toString, config)

    val jobList = actio.datapipes.application.Scheduler.getJobSchedule(pipeScript)

    jobList.foreach(j => {
      if(scheduler.checkExists(j._1.getKey))
        scheduler.deleteJob(j._1.getKey)
      scheduler.scheduleJob(j._1, j._2)
    })

  }
}
