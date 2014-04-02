package com.nitorcreations.cloudyplugin;

import static org.junit.Assert.*;

import java.util.regex.Pattern;

import org.junit.Test;

public class RegexTest {
	@Test
	public void testRegex() {
		String user = "pasi";
		Pattern p = Pattern.compile("(,|^)" + user + "(,|$)");
		assertTrue(p.matcher("pasi,p4niemi").find());
		assertTrue(p.matcher("p4niemi,pasi").find());
		assertTrue(p.matcher("pasi,p4ööniemi").find());
		assertFalse(p.matcher("pasiöö,p4niemi").find());
		assertFalse(p.matcher("pasiöö,p4niemi").find());
	}

	@Test
	public void testRegex2() {
		String user = "pasiöö";
		Pattern p = Pattern.compile("([^\\p{L}\\p{Nd}]|^)" + user + "([^\\p{L}\\p{Nd}]|$)");
		assertFalse(p.matcher("pasi,p4niemi").find());
		assertFalse(p.matcher("p4niemi,pasi").find());
		assertFalse(p.matcher("pasi,p4ööniemi").find());
		assertTrue(p.matcher("pasiöö,p4niemi").find());
		assertFalse(p.matcher("").find());
	}
	@Test
	public void testRegex3() {
		String user = "";
		Pattern p = Pattern.compile("([^\\p{L}\\p{Nd}]|^)" + user + "([^\\p{L}\\p{Nd}]|$)");
		assertFalse(p.matcher("pasi,p4niemi").find());
		assertFalse(p.matcher("p4niemi,pasi").find());
		assertFalse(p.matcher("pasi,p4ööniemi").find());
		assertFalse(p.matcher("pasiöö,p4niemi").find());
	}

}
