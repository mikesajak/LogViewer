package org.mikesajak.logviewer.util

import scala.collection.Searching.{Found, InsertionPoint, SearchResult}
import scala.math.Ordering

object SearchingEx {
  def binarySearch[A, B](data: IndexedSeq[A], mapper: A => B, searchValue: B)(implicit ord: Ordering[B]): SearchResult = {
    binarySearch(data, mapper, searchValue, 0, data.size)
  }

  private def binarySearch[A, B](data: IndexedSeq[A], mapper: A => B, searchValue: B,
                                               startIdx: Int, endIdx: Int)(implicit ord: Ordering[B]): SearchResult = {
    if (ord.eq(startIdx, endIdx)) {
      if (mapper(data(startIdx)) == searchValue) Found(startIdx)
      else InsertionPoint(startIdx)
    } else {
      val midIdx = startIdx + (endIdx - startIdx) / 2

      if (ord.lteq(mapper(data(midIdx)), searchValue)) binarySearch(data, mapper, searchValue, startIdx, midIdx)
      else binarySearch(data, mapper, searchValue, midIdx, endIdx)
    }
  }
}
