package org.mikesajak.logviewer.log

class GlobalState {
  private var logStore: LogStore = ImmutableMemoryLogStore.empty

  def currentLogStore: LogStore = logStore
  def currentLogStore_=(logStore: LogStore): Unit = this.logStore = logStore

}
