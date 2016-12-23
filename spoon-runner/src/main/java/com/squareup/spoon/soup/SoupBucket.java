package com.squareup.spoon.soup;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;

public class SoupBucket {
	private LinkedList<TestIdentifier> tests;
	private float totalTime;
	
	public SoupBucket() {
		this.tests = new LinkedList<TestIdentifier>();
		this.totalTime = (float)0.0;
	}
	
	public void feed(TestIdentifier ti) {
		tests.add(ti);
		totalTime += ti.getUsedTime();
	}
	
	public float getTotalTime() {
		return totalTime;
	}
	
	public boolean flushToFile(File bucketFile) {
		BufferedWriter fileWriter = null;
		try {
			fileWriter = new BufferedWriter(new FileWriter(bucketFile));
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		if (fileWriter == null) {
			return false;
		}
		
		for (TestIdentifier ti : tests) {
			try {
				fileWriter.write(ti.getFullMethodName() + "\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		} 
		
		try {
			fileWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}
	
}
