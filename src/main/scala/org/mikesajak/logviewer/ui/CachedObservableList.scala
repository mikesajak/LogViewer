package org.mikesajak.logviewer.ui

import java.util

import com.typesafe.scalalogging.Logger
import javafx.collections.{ObservableList, ObservableListBase}

class CachedObservableList[E](source: ObservableList[E]) extends ObservableListBase[E] {
  private val logger: Logger = Logger[CachedObservableList[E]]
  private val cache = new util.WeakHashMap[Int, E]()
  private val stats = new Stats()

  override def get(index: Int): E = {
    var cachedValue = cache.get(index)
    if (cachedValue == null) {
      stats.miss()
      cachedValue = source.get(index)
      cache.put(index, cachedValue)
    } else stats.hit()

    if (stats.totalCount >= 10000) {
      logger.trace(s"Cache stats: $stats")
      stats.reset()
    }
    cachedValue
  }
  override def size(): Int = source.size()

  class Stats {
    private var totalCount0 = 0
    private var hitCount0 = 0
    private var missCount0 = 0

    def hit(): Unit = {
      totalCount0 += 1
      hitCount0 += 1
    }
    def miss(): Unit = {
      totalCount0 += 1
      missCount0 += 1
    }

    def totalCount: Int = totalCount0
    def hitCount: Int = hitCount0
    def missCount: Int = missCount0

    def reset(): Unit = {
      totalCount0 = 0
      hitCount0 = 0
      missCount0 = 0
    }

    override def toString =
      s"total count=$totalCount0, hit count=$hitCount0(${hitCount0 *100 / totalCount0}%), miss count=$missCount0(${missCount0*100/totalCount0}%)"
  }
}
