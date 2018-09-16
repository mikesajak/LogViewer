package org.mikesajak.logviewer.log

class SimpleMemoryLogStore(override val entries: IndexedSeq[LogEntry]) extends LogStore {
//  private var entries = Map[LogId, LogEntry]()
  override def entry(idx: Long) = entries(idx.toInt)

//  override def getEntry(id: LogId): Option[LogEntry] = entryMap.get(id)

}

object SimpleMemoryLogStore {
  def apply(entries: Seq[LogEntry]): LogStore = {
    new SimpleMemoryLogStore(entries.toIndexedSeq)
  }
}
