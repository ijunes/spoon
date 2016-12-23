package com.squareup.spoon.soup;

import java.util.Comparator;

public class TestComparator implements Comparator<TestIdentifier> {

  @Override
  public int compare(TestIdentifier arg0, TestIdentifier arg1) {
    int result = -1;
    
    // test case that has empty method name has high priority
    if (arg0.getTestName().length() == 0 && arg1.getTestName().length() != 0) {
    	result = -1;
    } else if (arg1.getTestName().length() == 0 && arg0.getTestName().length() != 0) {
    	result = 1;
    } else if (arg0.getUsedTime() < arg1.getUsedTime()) {
      result = 1;
    } else if (arg0.getUsedTime() == arg1.getUsedTime()) {
      result = 0;
    }
    // System.out.println("Arg0: " + arg0 + " Arg1: " + arg1 + " result: " +
    // result);

    return result;
  }

}
