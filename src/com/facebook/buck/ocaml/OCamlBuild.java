/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.ocaml;

import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import java.nio.file.Path;

/**
 * A build rule which preprocesses, compiles, and assembles an OCaml source.
 */
public class OCamlBuild extends AbstractBuildRule {

  private final OCamlBuildContext ocamlContext;
  private final SourcePath cCompiler;
  private final SourcePath cxxCompiler;

  public OCamlBuild(
      BuildRuleParams params,
      SourcePathResolver resolver,
      OCamlBuildContext ocamlContext,
      SourcePath cCompiler,
      SourcePath cxxCompiler) {
    super(params, resolver);
    this.ocamlContext = ocamlContext;
    this.cCompiler = cCompiler;
    this.cxxCompiler = cxxCompiler;
  }

  @Override
  protected ImmutableCollection<Path> getInputsToCompareToOutput() {
    return ocamlContext.getInput();
  }

  @Override
  protected RuleKey.Builder appendDetailsToRuleKey(RuleKey.Builder builder) {
    return ocamlContext.appendDetailsToRuleKey(builder)
        .setInput("cCompiler", cCompiler)
        .setInput("cxxCompiler", cxxCompiler);
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context,
      BuildableContext buildableContext) {
    Path baseArtifactDir = ocamlContext.getOutput().getParent();
    buildableContext.recordArtifactsInDirectory(baseArtifactDir);
    buildableContext.recordArtifactsInDirectory(
        baseArtifactDir.resolve(OCamlBuildContext.OCAML_COMPILED_DIR));
    buildableContext.recordArtifactsInDirectory(
        baseArtifactDir.resolve(OCamlBuildContext.OCAML_COMPILED_BYTECODE_DIR));
    return ImmutableList.of(
        new MakeCleanDirectoryStep(ocamlContext.getOutput().getParent()),
        new OCamlBuildStep(
            ocamlContext,
            getResolver().getPath(cCompiler),
            getResolver().getPath(cxxCompiler)));
  }

  @Override
  public Path getPathToOutputFile() {
    return ocamlContext.getOutput();
  }

}
