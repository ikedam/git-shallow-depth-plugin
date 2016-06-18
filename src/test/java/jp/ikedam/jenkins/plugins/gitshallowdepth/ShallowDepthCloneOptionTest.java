/*
 * The MIT License
 * 
 * Copyright (c) 2015 IKEDA Yasuyuki
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jp.ikedam.jenkins.plugins.gitshallowdepth;

import static org.junit.Assert.*;

import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.Combination;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleBuild;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CloneOption;

import java.util.Arrays;
import java.util.Collections;

import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 */
public class ShallowDepthCloneOptionTest {
    @ClassRule
    public static GitShallowDepthJenkinsRule j = new GitShallowDepthJenkinsRule();
    
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    
    /**
     * Test whether the passed {@link CloneOption} is preserved
     * after configuration.
     * 
     * @param shallowClone
     * @param projectClass
     * @throws Exception
     */
    @SuppressWarnings("rawtypes") // Somehow specifing generics for AbstractProject causes a compilation error.
    private <T extends AbstractProject & TopLevelItem> void doTestConfigure(ShallowDepthCloneOption shallowClone, Class<T> projectClass) throws Exception {
        final String url = "https://github.com/jenkinsci/jenkins";
        T p = j.jenkins.createProject(projectClass, j.createUniqueProjectName());
        GitSCM scm = new GitSCM(
                Arrays.asList(new UserRemoteConfig(url, "", "", "")),
                Arrays.asList(new BranchSpec("*/master")),
                false,  // doGenerateSubmoduleConfigurations
                Collections.<SubmoduleConfig>emptyList(),
                null,   // browser
                null,   // gitTool
                Arrays.<GitSCMExtension>asList(shallowClone)
        );
        p.setScm(scm);
        j.configRoundtrip(p);
        j.assertEqualDataBoundBeans(scm, p.getScm());
    }
    
    @Test
    public void testConfigureWithNoDepth() throws Exception {
        ShallowDepthCloneOption clone = new ShallowDepthCloneOption(null);
        doTestConfigure(clone, FreeStyleProject.class);
    }
    
    @Test
    public void testConfigureWithDepth() throws Exception {
        ShallowDepthCloneOption clone = new ShallowDepthCloneOption(5);
        doTestConfigure(clone, FreeStyleProject.class);
    }
    
    @Test
    public void testConfigureWithDisableMatrixParent() throws Exception {
        ShallowDepthCloneOption clone = new ShallowDepthCloneOption(null);
        clone.setDisableForMatrixParent(true);
        doTestConfigure(clone, MatrixProject.class);
    }
    
    @Test
    public void testConfigureWithoutDisableMatrixParent() throws Exception {
        ShallowDepthCloneOption clone = new ShallowDepthCloneOption(null);
        clone.setDisableForMatrixParent(false);
        doTestConfigure(clone, MatrixProject.class);
    }
    
    private TaskListener createListener() throws Exception {
        return StreamBuildListener.fromStderr();
    }
    
    private final int COMMITS = 10;
    
    private TestGitRepo createRepo() throws Exception {
        TestGitRepo repo = new TestGitRepo(
                "repo",
                tmp.newFolder(),
                createListener()
        );
        for (int i = 1; i <= COMMITS; ++i) {
            repo.commit(
                    "afile",
                    Integer.toString(i),
                    repo.johnDoe,
                    String.format("Commit %d", i)
            );
        }
        return repo;
    }
    
    @SuppressWarnings("rawtypes") // Somehow specifing generics for AbstractProject causes a compilation error.
    private <T extends AbstractProject & TopLevelItem> T createProjectForTest(ShallowDepthCloneOption shallowClone, Class<T> projectClass) throws Exception {
        T p = j.jenkins.createProject(projectClass, j.createUniqueProjectName());
        GitSCM scm = new GitSCM(
                createRepo().remoteConfigs(),
                Arrays.asList(new BranchSpec("*/master")),
                false,  // doGenerateSubmoduleConfigurations
                Collections.<SubmoduleConfig>emptyList(),
                null,   // browser
                null,   // gitTool
                Arrays.<GitSCMExtension>asList(shallowClone)
        );
        p.setScm(scm);
        
        return p;
    }
    
    @Test
    public void testCloneWithoutDepth() throws Exception {
        ShallowDepthCloneOption shallowClone = new ShallowDepthCloneOption(null);
        FreeStyleProject p = createProjectForTest(shallowClone, FreeStyleProject.class);
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        // jgit fails to handle a repository with only one commit.
        GitClient git = Git.with(createListener(), null)
                .in(b.getWorkspace())
                .using("git")
                .getClient();
        assertEquals(1, git.revList("HEAD").size());
    }
    
    @Test
    public void testCloneWithDepth() throws Exception {
        ShallowDepthCloneOption shallowClone = new ShallowDepthCloneOption(5);
        FreeStyleProject p = createProjectForTest(shallowClone, FreeStyleProject.class);
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        GitClient git = Git.with(createListener(), null)
                .in(b.getWorkspace())
                .getClient();
        assertEquals(5, git.revList("HEAD").size());
    }
    
    @Test
    public void testCloneWithDisableMatrixParent() throws Exception {
        ShallowDepthCloneOption shallowClone = new ShallowDepthCloneOption(null);
        shallowClone.setDisableForMatrixParent(true);
        MatrixProject p = createProjectForTest(shallowClone, MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "value1")
        );
        p.setAxes(axes);
        MatrixBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        
        // parent
        {
            GitClient git = Git.with(createListener(), null)
                    .in(b.getWorkspace())
                    .using("git")
                    .getClient();
            assertEquals(COMMITS, git.revList("HEAD").size());
        }
        
        // child
        {
            GitClient git = Git.with(createListener(), null)
                    .in(b.getExactRun(new Combination(axes, "value1")).getWorkspace())
                    .using("git")
                    .getClient();
            assertEquals(1, git.revList("HEAD").size());
        }
    }
    
    @Test
    public void testCloneWithoutDisableMatrixParent() throws Exception {
        ShallowDepthCloneOption shallowClone = new ShallowDepthCloneOption(null);
        shallowClone.setDisableForMatrixParent(false);
        MatrixProject p = createProjectForTest(shallowClone, MatrixProject.class);
        AxisList axes = new AxisList(
                new Axis("axis1", "value1")
        );
        p.setAxes(axes);
        MatrixBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        
        // parent
        {
            GitClient git = Git.with(createListener(), null)
                    .in(b.getWorkspace())
                    .using("git")
                    .getClient();
            assertEquals(1, git.revList("HEAD").size());
        }
        
        // child
        {
            GitClient git = Git.with(createListener(), null)
                    .in(b.getExactRun(new Combination(axes, "value1")).getWorkspace())
                    .using("git")
                    .getClient();
            assertEquals(1, git.revList("HEAD").size());
        }
    }
}
