package com.glass.headunit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class SpikeLogTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void linesSurviveARestart() throws Exception {
        File f = new File(tmp.getRoot(), "panel.log");
        Spike.attachFile(f);
        Spike.log("first line");
        Spike.log("second line");
        Spike.detachForTest();                 // the process "dies": memory is gone, the file remains
        assertFalse(Spike.dump().contains("first line"));
        Spike.attachFile(f);
        String d = Spike.dump();
        assertTrue(d.contains("first line"));
        assertTrue(d.contains("second line"));
        assertTrue(d.contains("earlier run"));
        Spike.detachForTest();
    }

    @Test
    public void theFileStaysSmallAndTheDumpStaysShort() throws Exception {
        File f = new File(tmp.getRoot(), "panel.log");
        Spike.attachFile(f);
        for (int i = 0; i < 5000; i++) Spike.log("line number " + i + " with some padding to make it longer");
        Spike.detachForTest();
        assertTrue("file was " + f.length(), f.length() < 100_000);
        Spike.attachFile(f);
        assertTrue(Spike.dump().split("\n").length <= 202);
        assertTrue(Spike.dump().contains("line number 4999"));
        Spike.detachForTest();
    }
}
