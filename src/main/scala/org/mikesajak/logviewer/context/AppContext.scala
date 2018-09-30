package org.mikesajak.logviewer.context

import com.google.inject._
import net.codingwell.scalaguice.ScalaModule

object AppContext {
  val globalInjector: Injector = Guice.createInjector(new AppContext)
}

class AppContext extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    install(new UIContext)
    install(new LogStoreContext)
  }
}