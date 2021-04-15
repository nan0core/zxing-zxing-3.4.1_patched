/*
 * Copyright 2013 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.oned;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.common.BitArray;

import java.util.Arrays;
import java.util.Map;

/**
 * <p>Decodes MSI barcodes.</p>
 *
 * @author zxingnet.codeplex.com
 */
public final class MSIReader extends OneDReader {

  static final String ALPHABET_STRING = "0123456789";
  private static final char[] ALPHABET = ALPHABET_STRING.toCharArray();

  /**
   * These represent the encodings of characters, as patterns of wide and narrow bars.
   * The 9 least-significant bits of each int correspond to the pattern of wide and narrow,
   * with 1s representing "wide" and 0s representing narrow.
   */
  static final int[] CHARACTER_ENCODINGS = {
    0x924, 0x926, 0x934, 0x936, 0x9A4, 0x9A6, 0x9B4, 0x9B6, 0xD24, 0xD26 // 0-9
  };

  private static final int START_ENCODING = 0x06;
  private static final int END_ENCODING = 0x09;

  private final boolean usingCheckDigit;
  private final StringBuilder decodeRowResult;
  private final int[] counters;
  private int averageCounterWidth;

  /**
   * Creates a reader that assumes all encoded data is data, and does not treat the final
   * character as a check digit. It will not decoded "extended Code 39" sequences.
   */
  public MSIReader() {
    this(false);
  }

  /**
   * Creates a reader that can be configured to check the last character as a check digit.
   * It will not decoded "extended Code 39" sequences.
   *
   * @param usingCheckDigit if true, treat the last data character as a check digit, not
   * data, and verify that the checksum passes.
   */
  public MSIReader(boolean usingCheckDigit) {
    this.usingCheckDigit = usingCheckDigit;
    decodeRowResult = new StringBuilder(20);
    counters = new int[8];
  }

  @Override
  public Result decodeRow(int rowNumber, BitArray row, Map<DecodeHintType,?> hints)
      throws NotFoundException, ChecksumException, FormatException {

   int[] theCounters = counters;
   Arrays.fill(theCounters, 0);
   StringBuilder result = decodeRowResult;
   result.setLength(0);

   int[] start = findStartPattern(row, counters);

   // Read off white space
   int nextStart = row.getNextSet(start[1]);

   char decodedChar;
   int lastStart = nextStart;
   int pattern;
   do {
     try {
       recordPattern(row, nextStart, theCounters, 8);
     } catch (NotFoundException nf) {
       // not enough bars for a number but perhaps enough for the end pattern
       int[] endPattern = findEndPattern(row, nextStart, counters);
       lastStart = nextStart;
       nextStart = endPattern[1];
       break;
     }
     pattern = toPattern(counters, 8);
     try {
       decodedChar = patternToChar(pattern);
     } catch (NotFoundException nf) {
       // pattern doesn't result in an encoded number
       // but it could be the end pattern followed by some black areas
       int[] endPattern = findEndPattern(row, nextStart, counters);
       lastStart = nextStart;
       nextStart = endPattern[1];
       break;
     }
     result.append(decodedChar);
     lastStart = nextStart;
     for (int counter : theCounters) {
       nextStart += counter;
     }
     // Read off white space
     nextStart = row.getNextSet(nextStart);
   } while (decodedChar != '*');

   if (result.length() < 1) {
     throw NotFoundException.getNotFoundInstance();
   }

   String resultString = result.toString();

   if (usingCheckDigit) {
     if (decodeRowResult.length() < 2) {
       throw NotFoundException.getNotFoundInstance();
     }
     String resultStringWithoutChecksum = resultString.substring(0, resultString.length() - 1);
     int checkSum = calculateChecksum(resultStringWithoutChecksum);
     if ((char) (checkSum + 48) != resultString.charAt(resultStringWithoutChecksum.length())) {
        return null;
     }
   }

   float left = (float) (start[1] + start[0]) / 2.0f;
   float right = (float) (nextStart + lastStart) / 2.0f;

   ResultPointCallback resultPointCallback = hints == null ? null :
     (ResultPointCallback) hints.get(DecodeHintType.NEED_RESULT_POINT_CALLBACK);
   if (resultPointCallback != null) {
     resultPointCallback.foundPossibleResultPoint(new ResultPoint(left, rowNumber));
     resultPointCallback.foundPossibleResultPoint(new ResultPoint(right, rowNumber));
   }

   return new Result(
     resultString,
     null,
     new ResultPoint[] {
           new ResultPoint(left, rowNumber),
           new ResultPoint(right, rowNumber)
     },
     BarcodeFormat.MSI);
  }

  private int[] findStartPattern(BitArray row, int[] counters) throws NotFoundException {
    int patternLength = 2;

    int width = row.getSize();
    int rowOffset = row.getNextSet(0);

    int counterPosition = 0;
    int patternStart = rowOffset;
    boolean isWhite = false;

    counters[0] = 0;
    counters[1] = 0;
    for (int i = rowOffset; i < width; i++) {
      if (row.get(i) ^ isWhite) {
        counters[counterPosition]++;
      } else {
        if (counterPosition == patternLength - 1) {
          calculateAverageCounterWidth(counters, patternLength);
          if (toPattern(counters, patternLength) == START_ENCODING) {
            // Look for whitespace before start pattern, >= 50% of width of start pattern
            if (row.isRange(Math.max(0, patternStart - ((i - patternStart) >> 1)), patternStart, false)) {
              return new int[]{patternStart, i};
            }
          }
          patternStart += counters[0] + counters[1];
          System.arraycopy(counters, 2, counters, 0, patternLength - 2);
          counters[patternLength - 2] = 0;
          counters[patternLength - 1] = 0;
          counterPosition--;
        } else {
          counterPosition++;
        }
        counters[counterPosition] = 1;
        isWhite = !isWhite;
      }
    }
    throw NotFoundException.getNotFoundInstance();
  }

  private int[] findEndPattern(BitArray row, int rowOffset, int[] counters) throws NotFoundException {
    int patternLength = 3;

    int width = row.getSize();

    int counterPosition = 0;
    int patternStart = rowOffset;
    boolean isWhite = false;

    counters[0] = 0;
    counters[1] = 0;
    counters[2] = 0;
    for (int i = rowOffset; i < width; i++) {
      if (row.get(i) ^ isWhite) {
        counters[counterPosition]++;
      } else {
        if (counterPosition == patternLength - 1) {
          if (toPattern(counters, patternLength) == END_ENCODING) {
            // Look for whitespace after end pattern, >= 50% of width of end pattern
            int minEndOfWhite = Math.min(width - 1, i + ((i - patternStart) >> 1));
            if (row.isRange(i, minEndOfWhite, false)) {
              return new int[] { patternStart, i };
            }
          }
          throw NotFoundException.getNotFoundInstance();
        }
        counterPosition++;
        counters[counterPosition] = 1;
        isWhite = !isWhite;
      }
    }
    throw NotFoundException.getNotFoundInstance();
  }

  private void calculateAverageCounterWidth(int[] counters, int patternLength) {
     // look for the minimum and the maximum width of the bars
     // there are only two sizes for MSI barcodes
     // all numbers are encoded as a chain of the pattern 100 and 110
     // the complete pattern of one number always starts with 1 or 11 (black bar(s))
     int minCounter = Integer.MAX_VALUE;
     int maxCounter = 0;
     for (int index = 0; index < patternLength; index++) {
       int counter = counters[index];
        if (counter < minCounter) {
           minCounter = counter;
        }
        if (counter > maxCounter) {
           maxCounter = counter;
        }
     }
     // calculate the average of the minimum and maximum width
     // using some bit shift to get a higher resolution without floating point arithmetic
     averageCounterWidth = ((maxCounter << 8) + (minCounter << 8)) / 2;
  }

  private int toPattern(int[] counters, int patternLength) {
    // calculating the encoded value from the pattern
    int pattern = 0;
    int bit = 1;
    int doubleBit = 3;
    for (int index = 0; index < patternLength; index++) {
      int counter = counters[index];
      if ((counter << 8) < averageCounterWidth) {
        pattern = (pattern << 1) | bit;
      } else {
        pattern = (pattern << 2) | doubleBit;
      }
      bit = bit ^ 1;
      doubleBit = doubleBit ^ 3;
    }
    return pattern;
  }

  private static char patternToChar(int pattern) throws NotFoundException {
    for (int i = 0; i < CHARACTER_ENCODINGS.length; i++) {
      if (CHARACTER_ENCODINGS[i] == pattern) {
        return ALPHABET[i];
      }
    }
    throw NotFoundException.getNotFoundInstance();
  }

  private static final int[] doubleAndCrossSum = new int[] { 0, 2, 4, 6, 8, 1, 3, 5, 7, 9 };

  private static int calculateChecksum(String number) {
    int checksum = 0;

    for (int index = number.length() - 2; index >= 0; index -= 2) {
      int digit = number.charAt(index) - 48;
      checksum += digit;
    }
    for (int index = number.length() - 1; index >= 0; index -= 2) {
      int digit = doubleAndCrossSum[number.charAt(index) - 48];
      checksum += digit;
    }

    return (10 - (checksum % 10)) % 10;
  }
}
