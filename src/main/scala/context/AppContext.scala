package context

import com.google.inject._
import net.codingwell.scalaguice.ScalaModule
import org.mikesajak.logviewer.{AppController, OperationMgr}
import org.mikesajak.logviewer.config.Configuration
import org.mikesajak.logviewer.log.{LogParserMgr, LogStore, SimpleMemoryLogStore}
import org.mikesajak.logviewer.util.{EventBus, ResourceManager}

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
  def provideOperationManager(appController: AppController, eventBus: EventBus): OperationMgr = {
    new OperationMgr(appController, eventBus)
  }

}

class LogStoreContext extends AbstractModule with ScalaModule {
  override def configure(): Unit = {}

  @Provides
  @Singleton
  def provideLogParserMgr(eventBus: EventBus): LogParserMgr =
    new LogParserMgr(eventBus)
}

object AppContext {
  val globalInjector: Injector = Guice.createInjector(new AppContext)
}
