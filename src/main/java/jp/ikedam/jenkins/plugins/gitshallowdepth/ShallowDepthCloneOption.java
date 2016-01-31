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

import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.Run;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.extensions.GitSCMExtension;

/**
 * Enables shallow clone and specify its depth.
 */
public class ShallowDepthCloneOption extends GitSCMExtension {
    private final Integer depth;
    
    public ShallowDepthCloneOption() {
        this(null);
    }
    
    @DataBoundConstructor
    public ShallowDepthCloneOption(Integer depth) {
        this.depth = depth;
    }
    
    public Integer getDepth() {
        return depth;
    }
    
    @Override
    public void decorateCloneCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, org.jenkinsci.plugins.gitclient.CloneCommand cmd)
            throws IOException, InterruptedException, GitException
    {
        listener.getLogger().println("Using shallow clone");
        if (getDepth() != null) {
            listener.getLogger().format("  with depth {0}", getDepth());
        }
        cmd.shallow();
        cmd.depth(getDepth());
    }
    
    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.ShallowDepthCloneOption_DisplayName();
        }
    }
}
