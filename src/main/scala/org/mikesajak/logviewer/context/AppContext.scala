package org.mikesajak.logviewer.context

import com.google.inject._
import net.codingwell.scalaguice.ScalaModule
import org.mikesajak.logviewer.config.Configuration
import org.mikesajak.logviewer.log.{GlobalState, LogParserMgr}
import org.mikesajak.logviewer.ui.ConsoleProgressHandler
import org.mikesajak.logviewer.util.{EventBus, ResourceManager}
import org.mikesajak.logviewer.{AppController, OperationMgr}

class AppContext extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    install(new UIContext)
    install(new LogStoreContext)
  }
}

class UIContext extends AbstractModule with ScalaModule {
  override def configure(): Unit = {}

  @Provides
  @Singleton
  def provideAppController(config: Configuration) = new AppController(config)

  @Provides
  @Singleton
  def provideEventBus(): EventBus = new EventBus()

  @Provides
  @Singleton
  def provideConfig(eventBus: EventBus): Configuration = {
    val config = new Configuration(s"${AppController.configPath}/${AppController.configFile}",
                                   eventBus)
    config.load()
    config
  }

  @Provides
  @Singleton
  def provideResourceManager(): ResourceManager = new ResourceManager("logviewer")

  @Provides
  @Singleton
  def provideOperationManager(appController: AppController, eventBus: EventBus, config: Configuration): OperationMgr = {
    new OperationMgr(appController, eventBus, config)
  }

  @Provides
  @Singleton
  def provideProgressHandler(eventBus: EventBus) = new ConsoleProgressHandler(eventBus)

}

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

object AppContext {
  val globalInjector: Injector = Guice.createInjector(new AppContext)
}
