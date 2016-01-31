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
import hudson.model.FreeStyleBuild;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
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
import java.util.List;

import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 */
public class ShallowDepthCloneOptionTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    
    /**
     * Test whether the passed {@link CloneOption} is preserved
     * after configuration.
     * 
     * @param clone
     * @throws Exception
     */
    private void doTestConfigure(ShallowDepthCloneOption shallowClone) throws Exception {
        final String url = "https://github.com/jenkinsci/jenkins";
        FreeStyleProject p = j.createFreeStyleProject();
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
        doTestConfigure(clone);
    }
    
    @Test
    public void testConfigureWithDepth() throws Exception {
        ShallowDepthCloneOption clone = new ShallowDepthCloneOption(5);
        doTestConfigure(clone);
    }
    
    private TaskListener createListener() throws Exception {
        return StreamBuildListener.fromStderr();
    }
    
    private TestGitRepo createRepo() throws Exception {
        TestGitRepo repo = new TestGitRepo(
                "repo",
                tmp.newFolder(),
                createListener()
        );
        for (int i = 1; i <= 10; ++i) {
            repo.commit(
                    "afile",
                    Integer.toString(i),
                    repo.johnDoe,
                    String.format("Commit %d", i)
            );
        }
        return repo;
    }
    
    private FreeStyleProject createProjectForTest(ShallowDepthCloneOption shallowClone) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
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
        FreeStyleProject p = createProjectForTest(shallowClone);
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
        FreeStyleProject p = createProjectForTest(shallowClone);
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        GitClient git = Git.with(createListener(), null)
                .in(b.getWorkspace())
                .getClient();
        assertEquals(5, git.revList("HEAD").size());
    }
}
