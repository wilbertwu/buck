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

package com.facebook.buck.apple;

import static com.facebook.buck.apple.xcode.ProjectGeneratorTestUtils.createAppleBundleBuildRule;
import static com.facebook.buck.apple.xcode.ProjectGeneratorTestUtils.createDescriptionArgWithDefaults;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.cli.BuckConfig;
import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.cxx.CxxBuckConfig;
import com.facebook.buck.cxx.CxxLibraryDescription;
import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.DefaultCxxPlatform;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.Label;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.rules.coercer.Either;
import com.facebook.buck.testutil.TargetGraphFactory;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class AppleBuildRulesTest {

  private AppleLibraryDescription appleLibraryDescription;
  private AppleBundleDescription appleBundleDescription;
  private AppleTestDescription appleTestDescription;

  @Before
  public void setUp() throws IOException {
    BuckConfig buckConfig = new FakeBuckConfig();
    CxxPlatform cxxPlatform = new DefaultCxxPlatform(buckConfig);
    FlavorDomain<CxxPlatform> cxxPlatforms = new FlavorDomain<>(
        "C/C++ Platform",
        ImmutableMap.of(cxxPlatform.asFlavor(), cxxPlatform));
    appleLibraryDescription = new AppleLibraryDescription(
        new AppleConfig(buckConfig),
        new CxxLibraryDescription(new CxxBuckConfig(buckConfig), cxxPlatforms));
    appleBundleDescription = new AppleBundleDescription();
    appleTestDescription = new AppleTestDescription();
  }

  @Test
  public void testAppleLibraryIsXcodeTargetBuildRuleType() throws Exception {
    assertTrue(AppleBuildRules.isXcodeTargetBuildRuleType(AppleLibraryDescription.TYPE));
  }

  @Test
  public void testIosResourceIsNotXcodeTargetBuildRuleType() throws Exception {
    assertFalse(AppleBuildRules.isXcodeTargetBuildRuleType(AppleResourceDescription.TYPE));
  }

  @Test
  public void testAppleTestIsXcodeTargetTestBuildRuleType() throws Exception {
    BuildRuleResolver resolver = new BuildRuleResolver();
    BuildRuleParams libraryParams =
        new FakeBuildRuleParamsBuilder(BuildTarget.builder("//foo", "lib").build())
            .setType(AppleLibraryDescription.TYPE)
            .build();
    AppleNativeTargetDescriptionArg libraryArg =
        createDescriptionArgWithDefaults(appleLibraryDescription);
    BuildRule libraryRule =
        appleLibraryDescription.createBuildRule(libraryParams, resolver, libraryArg);
    resolver.addToIndex(libraryRule);

    BuildRule xctestRule = createAppleBundleBuildRule(
        BuildTarget.builder("//foo", "xctest").build(),
        resolver,
        appleBundleDescription,
        libraryRule,
        AppleBundleExtension.XCTEST);
    resolver.addToIndex(xctestRule);

    BuildRuleParams params =
        new FakeBuildRuleParamsBuilder(BuildTarget.builder("//foo", "test").build())
            .setDeps(ImmutableSortedSet.of(xctestRule))
            .setType(AppleTestDescription.TYPE)
            .build();

    AppleTestDescription.Arg arg =
        appleTestDescription.createUnpopulatedConstructorArg();
    arg.testBundle = xctestRule.getBuildTarget();
    arg.contacts = Optional.of(ImmutableSortedSet.<String>of());
    arg.labels = Optional.of(ImmutableSortedSet.<Label>of());
    arg.deps = Optional.of(ImmutableSortedSet.of(xctestRule.getBuildTarget()));
    arg.sourceUnderTest = Optional.of(ImmutableSortedSet.<BuildTarget>of());

    BuildRule testRule = appleTestDescription.createBuildRule(
        params,
        resolver,
        arg);
    resolver.addToIndex(testRule);

    assertTrue(AppleBuildRules.isXcodeTargetTestBuildRule(testRule));
  }

  @Test
  public void testAppleLibraryIsNotXcodeTargetTestBuildRuleType() throws Exception {
    BuildRuleParams params =
        new FakeBuildRuleParamsBuilder(BuildTarget.builder("//foo", "lib").build())
            .setType(AppleLibraryDescription.TYPE)
            .build();
    AppleNativeTargetDescriptionArg arg =
        createDescriptionArgWithDefaults(appleLibraryDescription);
    BuildRule libraryRule =
        appleLibraryDescription.createBuildRule(params, new BuildRuleResolver(), arg);

    assertFalse(AppleBuildRules.isXcodeTargetTestBuildRule(libraryRule));
  }

  @Test
  public void testXctestIsTestBundleExtension() throws Exception {
    assertTrue(AppleBuildRules.isXcodeTargetTestBundleExtension(AppleBundleExtension.XCTEST));
  }

  @Test
  public void testOctestIsTestBundleExtension() throws Exception {
    assertTrue(AppleBuildRules.isXcodeTargetTestBundleExtension(AppleBundleExtension.OCTEST));
  }

  @Test
  public void testRecursiveTargetsIncludesBundleBinaryFromOutsideBundle() throws Exception {
    BuildTarget libraryTarget = BuildTarget.builder("//foo", "lib").build();
    TargetNode<?> libraryNode = AppleLibraryBuilder
        .createBuilder(libraryTarget)
        .build();

    BuildTarget bundleTarget = BuildTarget.builder("//foo", "bundle").build();
    TargetNode<?> bundleNode = AppleBundleBuilder
        .createBuilder(bundleTarget)
        .setExtension(Either.<AppleBundleExtension, String>ofLeft(AppleBundleExtension.XCTEST))
        .setBinary(libraryTarget)
        .build();

    BuildTarget rootTarget = BuildTarget.builder("//foo", "root").build();
    TargetNode<?> rootNode = AppleLibraryBuilder
        .createBuilder(rootTarget)
        .setDeps(Optional.of(ImmutableSortedSet.of(libraryTarget, bundleTarget)))
        .build();

    Iterable<TargetNode<?>> rules = AppleBuildRules.getRecursiveTargetNodeDependenciesOfTypes(
        TargetGraphFactory.newInstance(ImmutableSet.of(libraryNode, bundleNode, rootNode)),
        AppleBuildRules.RecursiveDependenciesMode.BUILDING,
        rootNode,
        Optional.<ImmutableSet<BuildRuleType>>absent());

    assertTrue(Iterables.elementsEqual(ImmutableSortedSet.of(libraryNode, bundleNode), rules));
  }
}
