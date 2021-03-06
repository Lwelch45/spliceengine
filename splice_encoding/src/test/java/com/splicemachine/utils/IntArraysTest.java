/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.utils;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test the IntArrays utility
 *
 * @author Jeff Cunningham
 *         Date: 8/8/14
 */
public class IntArraysTest {

    @Test
    public void testComplementMap() throws Exception {
        int[] expected = new int[] {0,3,4,5};
        int[] input = new int[] {1,2};
        int size = expected.length + input.length;

        int[] complement = IntArrays.complementMap(input,size);
        Assert.assertArrayEquals(printArrays(input,size,expected,complement),expected,complement);
    }

    @Test
    public void testComplementMap1() throws Exception {
        int[] expected = new int[] {3,4,5};
        int[] input = new int[] {0,1,2};
        int size = expected.length + input.length;

        int[] complement = IntArrays.complementMap(input,size);
        Assert.assertArrayEquals(printArrays(input,size,expected,complement),expected,complement);
    }

    @Test
    public void testComplementMap2() throws Exception {
        int[] expected = new int[] {0,1,2};
        int[] input = new int[] {3,4,5};
        int size = expected.length + input.length;

        int[] complement = IntArrays.complementMap(input,size);
        Assert.assertArrayEquals(printArrays(input,size,expected,complement),expected,complement);
    }

    @Test
    public void testComplementMap3() throws Exception {
        int[] expected = new int[] {};
        int[] input = new int[] {0,1,2,3,4,5};
        int size = expected.length + input.length;

        int[] complement = IntArrays.complementMap(input,size);
        Assert.assertArrayEquals(printArrays(input,size,expected,complement),expected,complement);
    }

    @Test
    public void testComplementMap4() throws Exception {
        int[] expected = new int[] {0,1,2,3,4,5};
        int[] input = new int[] {};
        int size = expected.length + input.length;

        int[] complement = IntArrays.complementMap(input,size);
        Assert.assertArrayEquals(printArrays(input,size,expected,complement),expected,complement);
    }

    @Test
    public void testComplementMap5() throws Exception {
        int[] expected = new int[] {1,2,3,4};
        int[] input = new int[] {0};
        int size = expected.length + input.length;

        int[] complement = IntArrays.complementMap(input,size);
        Assert.assertArrayEquals(printArrays(input,size,expected,complement),expected,complement);
    }

    @Test
    public void testComplementMap6() throws Exception {
        int[] expected = new int[] {0,1,2,3};
        int[] input = new int[] {4};
        int size = expected.length + input.length;

        int[] complement = IntArrays.complementMap(input,size);
        Assert.assertArrayEquals(printArrays(input,size,expected,complement),expected,complement);
    }

    @Test
    public void testComplementMap6a() throws Exception {
        int[] expected = new int[] {0,1,2,3,5};
        int[] input = new int[] {4};
        int size = 6;

        int[] complement = IntArrays.complementMap(input,size);
        Assert.assertArrayEquals(printArrays(input,size,expected,complement),expected,complement);
    }

    @Test
    public void testComplementMap7() throws Exception {
        int[] expected = new int[] {1,2,3,4};
        int[] input = new int[] {0};
        int size = expected.length + input.length-1;

        try {
            IntArrays.complementMap(input,size);
            Assert.fail("Should have been an Assertion error - more missing fields than present.");
        } catch (AssertionError e) {
            // expected
        }
    }

    @Test
    public void testComplementMap8() throws Exception {
        int[] expected = new int[] {0,1,4,5};
        int[] input = new int[] {2,3};
        int size = expected.length + input.length;

        int[] complement = IntArrays.complementMap(input,size);
        Assert.assertArrayEquals(printArrays(input,size,expected,complement),expected,complement);
    }

    @Test
    public void testComplementMap9() throws Exception {
        int[] expected = new int[] {0,1,2,5};
        int[] input = new int[] {3,4};
        int size = expected.length + input.length;

        int[] complement = IntArrays.complementMap(input,size);
//        System.out.println(printArrays(input,size,expected,complement));
        Assert.assertArrayEquals(printArrays(input,size,expected,complement),expected,complement);
    }

    // =============================================================
    private String printArrays(int[] input, int size, int[] expected, int[] actual) {
        return "FilterMap: "+Arrays.toString(input) +" Size: "+size+ "\nResult: "+ Arrays.toString(expected) +
            "\nActual:   "+ Arrays.toString(actual)+"\n";
    }
}
