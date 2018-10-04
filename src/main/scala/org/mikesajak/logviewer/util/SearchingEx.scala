package org.mikesajak.logviewer.util

import scala.collection.Searching.{Found, InsertionPoint, SearchResult}
import scala.math.Ordering

object SearchingEx {
  def binarySearch[A, B](data: IndexedSeq[A], mapper: A => B, searchValue: B)(implicit ord: Ordering[B]): SearchResult = {
    binarySearch(data, mapper, searchValue, 0, data.size)
  }

  private def binarySearch[A, B](data: IndexedSeq[A], mapper: A => B, searchValue: B,
                                               startIdx: Int, endIdx: Int)(implicit ord: Ordering[B]): SearchResult = {
    if (startIdx == endIdx || startIdx == endIdx - 1) {
      if (mapper(data(startIdx)) == searchValue) Found(startIdx)
      else InsertionPoint(startIdx)
    } else {
      val midIdx = startIdx + (endIdx - startIdx) / 2

      val entryValue = mapper(data(midIdx))
      ord.compare(entryValue, searchValue) match {
        case 0 =>          Found(midIdx)
        case d if d > 0 => binarySearch(data, mapper, searchValue, startIdx, midIdx)
        case _ =>          binarySearch(data, mapper, searchValue, midIdx, endIdx)
      }
    }
  }
}
