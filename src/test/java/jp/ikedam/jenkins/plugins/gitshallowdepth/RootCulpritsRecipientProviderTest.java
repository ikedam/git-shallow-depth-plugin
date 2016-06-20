/*
 * The MIT License
 * 
 * Copyright (c) 2016 IKEDA Yasuyuki
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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.mail.internet.InternetAddress;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import hudson.Launcher;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.Combination;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.plugins.emailext.ExtendedEmailPublisher;
import hudson.plugins.emailext.ExtendedEmailPublisherContext;
import hudson.plugins.emailext.MatrixTriggerMode;
import hudson.plugins.emailext.plugins.EmailTrigger;
import hudson.plugins.emailext.plugins.RecipientProvider;
import hudson.plugins.emailext.plugins.recipients.CulpritsRecipientProvider;
import hudson.plugins.emailext.plugins.trigger.FailureTrigger;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.tasks.Fingerprinter;

/**
 * Tests for {@link RootCulpritsRecipientProvider}
 */
public class RootCulpritsRecipientProviderTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    
    @Test
    public void testCulprits() throws Exception {
        // Consider a following case:
        // 
        // upstream is a multi configuration project with a child axis1=value1.
        // downstream is a freestyle project that use an artifact of 
        //    upstream/axis1=value1.
        // 
        // downstream#1 succeeded with an artifact from upstream/axis1=value1#1
        // downstream#2 failed    with an artifact from upstream/axis1=value1#3
        // 
        // In this case, I want to send a mail to a user committed to upstream/axis1=value1#2
        
        // Create upstream#1 and downstream#1.
        TestGitRepo repo = new TestGitRepo(
                "repo",
                tmp.newFolder(),
                StreamBuildListener.fromStderr()
        );
        repo.commit(
                "afile",
                "initial file",
                repo.johnDoe,
                "Committed file for build#1"
        );
        
        MatrixProject upstream = j.createMatrixProject();
        AxisList axes = new AxisList(new Axis("axis1", "value1"));
        upstream.setAxes(axes);
        
        ShallowDepthCloneOption shallow = new ShallowDepthCloneOption();
        shallow.setDisableForMatrixParent(true);
        GitSCM scm = new GitSCM(
                repo.remoteConfigs(),
                Arrays.asList(new BranchSpec("*/master")),
                false,  // doGenerateSubmoduleConfigurations
                Collections.<SubmoduleConfig>emptyList(),
                null,   // browser
                null,   // gitTool
                Arrays.<GitSCMExtension>asList(shallow)
        );
        upstream.setScm(scm);
        
        upstream.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().child("artifact.txt").write(
                        build.getEnvironment(listener).expand("${JOB_URL}${GIT_COMMIT}"),
                        "UTF-8"
                );
                return true;
            }
        });
        upstream.getPublishersList().add(new Fingerprinter("artifact.txt"));
        
        MatrixBuild upstream1 = j.assertBuildStatusSuccess(upstream.scheduleBuild2(0));
        final String artifact1 = upstream1.getExactRun(new Combination(axes, "value1")).getWorkspace().child("artifact.txt").readToString();
        // remove workspace of child to clear git log.
        upstream1.getExactRun(new Combination(axes, "value1")).getWorkspace().deleteRecursive();
        
        FreeStyleProject downstream = j.createFreeStyleProject();
        downstream.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().child("artifact.txt").write(
                        artifact1,
                        "UTF-8"
                );
                return true;
            }
        });
        downstream.getPublishersList().add(new Fingerprinter("artifact.txt"));
        j.assertBuildStatusSuccess(downstream.scheduleBuild2(0));
        
        // Create upstream#2
        repo.commit(
                "afile",
                "updated for #2",
                repo.janeDoe,
                "Committed file for build#2"
        );
        MatrixBuild upstream2 = j.assertBuildStatusSuccess(upstream.scheduleBuild2(0));
        upstream2.getExactRun(new Combination(axes, "value1")).getWorkspace().deleteRecursive();
        
        // Create upstream#3, downstream#2
        repo.commit(
                "afile",
                "updated for #3",
                repo.johnDoe,
                "Committed file for build#3"
        );
        MatrixBuild upstream3 = j.assertBuildStatusSuccess(upstream.scheduleBuild2(0));
        final String artifact3 = upstream3.getExactRun(new Combination(axes, "value1")).getWorkspace().child("artifact.txt").readToString();
        upstream3.getExactRun(new Combination(axes, "value1")).getWorkspace().deleteRecursive();
        
        downstream.getBuildersList().clear();
        downstream.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().child("artifact.txt").write(
                        artifact3,
                        "UTF-8"
                );
                return false;   // failure!
            }
        });
        FreeStyleBuild downstream2 = j.assertBuildStatus(Result.FAILURE, downstream.scheduleBuild2(0).get());
        
        
        // Now try to extract targets to send a failure mail.
        ExtendedEmailPublisherContext context = new ExtendedEmailPublisherContext(
                new ExtendedEmailPublisher(),
                downstream2,
                StreamBuildListener.fromStderr()
        );
        Set<InternetAddress> to = new HashSet<InternetAddress>();
        Set<InternetAddress> cc = new HashSet<InternetAddress>();
        Set<InternetAddress> bcc = new HashSet<InternetAddress>();
        
        // CulpritsRecipientProvider provided by email-ext fails to extract
        // janeDoe as upstream/axis1=value1#2 doesn't have changelog.
        to.clear();
        cc.clear();
        bcc.clear();
        new CulpritsRecipientProvider().addRecipients(
                context,
                downstream2.getEnvironment(StreamBuildListener.fromStderr()),
                to,
                cc,
                bcc
        );
        assertThat(to, not(hasItem(InternetAddress.parse(repo.janeDoe.getEmailAddress())[0])));
        
        // CulpritsRecipientProvider provided by email-ext fails to extract
        // janeDoe as upstream/axis1=value1#2 doesn't have changelog.
        to.clear();
        cc.clear();
        bcc.clear();
        new RootCulpritsRecipientProvider().addRecipients(
                context,
                downstream2.getEnvironment(StreamBuildListener.fromStderr()),
                to,
                cc,
                bcc
        );
        assertThat(to, hasItem(InternetAddress.parse(repo.janeDoe.getEmailAddress())[0]));
    }
    
    @Test
    public void testConfiguration() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        ExtendedEmailPublisher emailExt = new ExtendedEmailPublisher(
                "test@example.com",
                "text/plain",
                "subject",
                "body",
                "",
                "",
                0,
                "",
                false,
                Arrays.<EmailTrigger>asList(new FailureTrigger(
                        Arrays.<RecipientProvider>asList(new RootCulpritsRecipientProvider()),
                        "",
                        "",
                        "",
                        "",
                        "",
                        0,
                        ""
                )),
                null
        );
        p.getPublishersList().add(emailExt);
        j.configRoundtrip(p);
        
        // ExtendedEmailPublisher and EmailTrigger doesn't implement
        // all properties of DataBoundConstructor.
        j.assertEqualDataBoundBeans(
                emailExt.getConfiguredTriggers().get(0).getEmail().getRecipientProviders(),
                p.getPublishersList().get(ExtendedEmailPublisher.class).getConfiguredTriggers().get(0).getEmail().getRecipientProviders()
        );
    }
}
