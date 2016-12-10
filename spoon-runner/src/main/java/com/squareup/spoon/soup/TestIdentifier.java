package com.squareup.spoon.soup;

public class TestIdentifier {
  private final String mClassName;
  private final String mTestName;
  private float mUsedTime;

  /**
   * Creates a test identifier.
   *
   * @param className
   *            fully qualified class name of the test. Cannot be null.
   * @param testName
   *            name of the test. Cannot be null.
   */
  public TestIdentifier(String className, String testName) {
    if (className == null || testName == null) {
      throw new IllegalArgumentException("className and testName must be non-null");
    }
    mClassName = className;
    mTestName = testName;
    mUsedTime = (float)0.01; // use to allocate case if there is no report
  }

  /**
   * Returns the fully qualified class name of the test.
   */
  public String getClassName() {
    return mClassName;
  }

  /**
   * Returns the name of the test.
   */
  public String getTestName() {
    return mTestName;
  }

  public float getUsedTime() {
    return mUsedTime;
  }

  public void setUsedTime(float usedTime) {
    mUsedTime = usedTime;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((mClassName == null) ? 0 : mClassName.hashCode());
    result = prime * result + ((mTestName == null) ? 0 : mTestName.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    TestIdentifier other = (TestIdentifier) obj;
    if (mClassName == null) {
      if (other.mClassName != null)
        return false;
    } else if (!mClassName.equals(other.mClassName))
      return false;
    if (mTestName == null) {
      if (other.mTestName != null)
        return false;
    } else if (!mTestName.equals(other.mTestName))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return String.format("%s#%s#%f", getClassName(), getTestName(), getUsedTime());
  }
  
  public String getFullMethodName() {
  	if (mTestName == null || mTestName.length() == 0) {
  		return mClassName;
  	}
  	return mClassName + "#" + mTestName; 
  }
}
