package com.nitorcreations.cloudyplugin;

import java.util.LinkedHashSet;

import org.codehaus.plexus.util.StringUtils;
import org.jclouds.compute.domain.OperatingSystem;
import org.jclouds.compute.predicates.OperatingSystemPredicates;

public abstract class PackageInstallerBuilder {
	protected final String mgrCommand;
	protected final LinkedHashSet<String> packages = new LinkedHashSet<>();
	
	protected PackageInstallerBuilder(String mgrCommand) {
		this.mgrCommand = mgrCommand;
	}
	public static PackageInstallerBuilder create(OperatingSystem os) {
		if (OperatingSystemPredicates.supportsApt().apply(os))
			  return new AptPackageInstaller();
			else if (OperatingSystemPredicates.supportsYum().apply(os))
			  return new YumPackageInstaller();
			else if (OperatingSystemPredicates.supportsZypper().apply(os))
	              return new ZypperPackageInstaller();
			else
			  throw new IllegalArgumentException("don't know how to handle" + os.toString());
	}
	
	public String build() {
		return command();
	}
	
	public PackageInstallerBuilder addPackage(String pkg) {
	    packages.add(pkg);
	    return this;
	}
    
	protected String command() {
        return mgrCommand + StringUtils.join(packages.iterator(), " ");
    }

	public static class AptPackageInstaller extends PackageInstallerBuilder {
		protected AptPackageInstaller() {
			super("apt-get install -y ");
		}
	}

	public static class YumPackageInstaller extends PackageInstallerBuilder {
        protected YumPackageInstaller() {
            super("yum install -y ");
        }
    }
	public static class ZypperPackageInstaller extends PackageInstallerBuilder {
        protected ZypperPackageInstaller() {
            super("zypper -n in ");
        }
    }
}
