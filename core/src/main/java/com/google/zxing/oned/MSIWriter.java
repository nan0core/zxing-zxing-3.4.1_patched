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
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;

import java.util.Map;

/**
 * This object renders a MSI code as a {@link BitMatrix}.
 *
 * @author zxingnet.codeplex.com
 */
public final class MSIWriter extends OneDimensionalCodeWriter {

  private static final int[] START_WIDTHS = new int[] { 2, 1 };
  private static final int[] END_WIDTHS = new int[] { 1, 2, 1 };
  private static final int[][] NUMBER_WIDTHS = new int[][]
                                                    {
                                                       new int[] { 1, 2, 1, 2, 1, 2, 1, 2 },
                                                       new int[] { 1, 2, 1, 2, 1, 2, 2, 1 },
                                                       new int[] { 1, 2, 1, 2, 2, 1, 1, 2 },
                                                       new int[] { 1, 2, 1, 2, 2, 1, 2, 1 },
                                                       new int[] { 1, 2, 2, 1, 1, 2, 1, 2 },
                                                       new int[] { 1, 2, 2, 1, 1, 2, 2, 1 },
                                                       new int[] { 1, 2, 2, 1, 2, 1, 1, 2 },
                                                       new int[] { 1, 2, 2, 1, 2, 1, 2, 1 },
                                                       new int[] { 2, 1, 1, 2, 1, 2, 1, 2 },
                                                       new int[] { 2, 1, 1, 2, 1, 2, 2, 1 }
                                                    };

  @Override
  public BitMatrix encode(String contents,
                          BarcodeFormat format,
                          int width,
                          int height,
                          Map<EncodeHintType,?> hints) throws IllegalArgumentException {
    if (format != BarcodeFormat.MSI) {
      throw new IllegalArgumentException("Can only encode MSI, but got " + format);
    }
    return super.encode(contents, format, width, height, hints);
  }

  @Override
  public boolean[] encode(String contents) {

    int length = contents.length();
    for (int i = 0; i < length; i++) {
       int indexInString = MSIReader.ALPHABET_STRING.indexOf(contents.charAt(i));
       if (indexInString < 0) {
         throw new IllegalArgumentException("Requested contents contains a not encodable character: '" + contents.charAt(i) + "'");
       }
    }

    int codeWidth = 3 + length * 12 + 4;
    boolean[] result = new boolean[codeWidth];
    int pos = appendPattern(result, 0, START_WIDTHS, true);
    for (int i = 0; i < length; i++) {
       int indexInString = MSIReader.ALPHABET_STRING.indexOf(contents.charAt(i));
       int[] widths = NUMBER_WIDTHS[indexInString];
       pos += appendPattern(result, pos, widths, true);
    }
    appendPattern(result, pos, END_WIDTHS, true);
    return result;
  }
}
