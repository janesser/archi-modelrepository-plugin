package org.archicontribs.modelrepository.grafico.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class GitExecutorTest {

	final File GIT_REPO = new File("/home/jan/projs/egit");
	final File GIT_PATH = new File("/snap/eclipse-pde/x12/usr/local/bin/bin/git");

	public static record GitExecutionResult(int exitCode, String outputLine) {} 

	public static class GitExecutionException extends Exception {

		private static final long serialVersionUID = 1L;

		public GitExecutionException(Throwable cause) {
			super(cause);
		}
		
	}
	
	public static class GitExecutor {
		
		private final File gitPath;
		private final File gitRepo;
		
		public GitExecutor(File gitPath, File gitRepo) {
			this.gitPath = gitPath;
			this.gitRepo = gitRepo;
		}

		private GitExecutionResult gitExec(File gitPath, File workingDir, String... gitCommands) throws IOException, InterruptedException {
			List<String> commandStrings = new ArrayList<String>(gitCommands.length+1);
			commandStrings.add(gitPath.getPath());
			commandStrings.addAll(Arrays.asList(gitCommands));
			
			ProcessBuilder pb = new ProcessBuilder(commandStrings.toArray(new String[] {}));
			pb.redirectErrorStream(true);
			pb.directory(workingDir);
			Process p = pb.start();
			BufferedReader br = new BufferedReader(
					new InputStreamReader(
							p.getInputStream()));	
			String line = br.readLine();
			return new GitExecutionResult(p.waitFor(), line);
		}
				
		public GitExecutionResult gitExec(String gitCommands) throws GitExecutionException {
			try {
				return gitExec(gitPath, gitRepo, gitCommands);
			} catch (IOException | InterruptedException ex) {
				throw new GitExecutionException(ex);
			}
		}
	}
	
	private GitExecutor underTest = new GitExecutor(GIT_PATH, GIT_REPO);
	
	
	@Disabled("due to snap construction")
	@Test
	public void canFindGitOnPath() throws GitExecutionException {
		GitExecutionResult res = new GitExecutor(new File("git"), new File("."))
				.gitExec("--version");
		System.out.println(res.outputLine);
		assertEquals(11, res.exitCode);
	}
	
	@Test
	public void canGitVersion() throws GitExecutionException {
		GitExecutionResult res = underTest.gitExec("--version");
		System.out.println(res.outputLine);
		assertEquals(0, res.exitCode);
	}

	@Test
	public void canGitFetch() throws GitExecutionException {
		GitExecutionResult res = underTest.gitExec("fetch");
		System.out.println(res.outputLine);
		assertEquals(0, res.exitCode);			
	}
}
