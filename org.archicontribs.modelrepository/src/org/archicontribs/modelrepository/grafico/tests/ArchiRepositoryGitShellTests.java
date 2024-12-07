package org.archicontribs.modelrepository.grafico.tests;

import java.io.File;

import org.archicontribs.modelrepository.grafico.ArchiRepository;
import org.junit.jupiter.api.Test;

public class ArchiRepositoryGitShellTests {

	final File localGitFolder = new File(ArchiRepositoryGitShellTests.class.getProtectionDomain().getCodeSource().getLocation().getPath());
	final ArchiRepository underTest = new ArchiRepository(localGitFolder);
	
	@Test
	public void testLocalGitFolder() {
		System.out.println(localGitFolder);
	}
}
