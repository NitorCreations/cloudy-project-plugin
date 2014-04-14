package com.nitorcreations.cloudyplugin;

import static org.junit.Assert.assertEquals;

import org.jclouds.compute.domain.OperatingSystem;
import org.jclouds.compute.domain.OsFamily;
import org.junit.Test;

public class PackageInstallerBuilderTest {
    @Test
    public void testPkgInstaller() {
        String pkgs = "jenkins,openjdk-1.7.0";
        OperatingSystem ubuntu = OperatingSystem.builder().family(OsFamily.UBUNTU).arch("x86_64").description("Ubuntu").build();
        PackageInstallerBuilder b = PackageInstallerBuilder.create(ubuntu);
        for (String next : pkgs.split(",")) {
            b.addPackage(next);
        }
        assertEquals("apt-get install -y jenkins openjdk-1.7.0", b.build());
        
        OperatingSystem centos = OperatingSystem.builder().family(OsFamily.CENTOS).arch("x86_64").description("Centos").build();
        b = PackageInstallerBuilder.create(centos);
        for (String next : pkgs.split(",")) {
            b.addPackage(next);
        }
        assertEquals("yum install -y jenkins openjdk-1.7.0", b.build());
    }

}
