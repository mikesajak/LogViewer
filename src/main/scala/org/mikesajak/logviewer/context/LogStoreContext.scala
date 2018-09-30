package org.mikesajak.logviewer.context

import com.google.inject.{AbstractModule, Provides, Singleton}
import net.codingwell.scalaguice.ScalaModule
import org.mikesajak.logviewer.log.{GlobalState, LogParserMgr}
import org.mikesajak.logviewer.util.EventBus

class LogStoreContext extends AbstractModule with ScalaModule {
  override def configure(): Unit = {}

  @Provides
  @Singleton
  def provideGlobalState(): GlobalState = new GlobalState

  @Provides
  @Singleton
  def provideLogParserMgr(eventBus: EventBus, globalState: GlobalState): LogParserMgr =
    new LogParserMgr(eventBus, globalState)
}
