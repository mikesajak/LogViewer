package org.mikesajak.logviewer.context

import com.google.inject.{AbstractModule, Provides, Singleton}
import net.codingwell.scalaguice.ScalaModule
import org.mikesajak.logviewer.config.Configuration
import org.mikesajak.logviewer.ui.{ConsoleProgressHandler, FilterExpressionParser}
import org.mikesajak.logviewer.util.{EventBus, ResourceManager}
import org.mikesajak.logviewer.{AppController, OperationMgr}

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

  @Provides
  @Singleton
  def provideFilterExpressionParser(resourceMgr: ResourceManager) =
    new FilterExpressionParser(resourceMgr)
}
