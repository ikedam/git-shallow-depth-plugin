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

import java.io.IOException;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.Plugin;
import hudson.matrix.MatrixProject;
import hudson.model.TaskListener;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import jenkins.model.Jenkins;
import hudson.plugins.git.extensions.GitSCMExtension;

/**
 * Enables shallow clone and specify its depth.
 */
public class ShallowDepthCloneOption extends GitSCMExtension {
    private final Integer depth;
    private boolean disableForMatrixParent;
    
    public ShallowDepthCloneOption() {
        this(null);
    }
    
    @DataBoundConstructor
    public ShallowDepthCloneOption(Integer depth) {
        this.depth = depth;
        this.disableForMatrixParent = false;
    }
    
    public Integer getDepth() {
        return depth;
    }
    
    @DataBoundSetter
    public void setDisableForMatrixParent(boolean disableForMatrixParent) {
        this.disableForMatrixParent = disableForMatrixParent;
    }
    
    public boolean isDisableForMatrixParent() {
        return disableForMatrixParent;
    }
    
    @Override
    public void decorateCloneCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, org.jenkinsci.plugins.gitclient.CloneCommand cmd)
            throws IOException, InterruptedException, GitException
    {
        if (isDisableForMatrixParent() && isMatrixParent(build.getParent())) {
            return;
        }
        listener.getLogger().println("Using shallow clone");
        if (getDepth() != null) {
            listener.getLogger().format("  with depth {0}", getDepth());
        }
        cmd.shallow();
        cmd.depth(getDepth());
    }
    
    private static boolean isMatrixParent(@Nonnull Job<?, ?> job) {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return false;
        }
        Plugin p = jenkins.getPlugin("matrix-project");
        if (p == null || !p.getWrapper().isActive()) {
            return false;
        }
        return (job instanceof MatrixProject);
    }
    
    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.ShallowDepthCloneOption_DisplayName();
        }
        
        public boolean isMatrixProject() {
            StaplerRequest req = Stapler.getCurrentRequest();
            if (req == null) {
                return false;
            }
            Job<?, ?> job = req.findAncestorObject(Job.class);
            if (job == null) {
                return false;
            }
            return isMatrixParent(job);
        }
    }
}
