/*
 * Copyright 2018 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.ncache.CacheReadEntry;
import com.google.cloud.tools.jib.ncache.storage.DefaultCacheReadEntry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.security.DigestException;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link BuildImageStep}. */
@RunWith(MockitoJUnitRunner.class)
public class BuildImageStepTest {

  @Mock private BuildConfiguration mockBuildConfiguration;
  @Mock private ContainerConfiguration mockContainerConfiguration;
  @Mock private JibLogger mockBuildLogger;
  @Mock private PullBaseImageStep mockPullBaseImageStep;
  @Mock private PullAndCacheBaseImageLayersStep mockPullAndCacheBaseImageLayersStep;
  @Mock private PullAndCacheBaseImageLayerStep mockPullAndCacheBaseImageLayerStep;
  @Mock private BuildAndCacheApplicationLayerStep mockBuildAndCacheApplicationLayerStep;

  private DescriptorDigest testDescriptorDigest;

  @Before
  public void setUp() throws DigestException {
    testDescriptorDigest =
        DescriptorDigest.fromHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    CacheReadEntry testCacheReadEntry =
        DefaultCacheReadEntry.builder()
            .setLayerDigest(testDescriptorDigest)
            .setLayerDiffId(testDescriptorDigest)
            .setLayerSize(-1)
            .setLayerBlob(Blobs.from("ignored"))
            .build();

    Mockito.when(mockBuildConfiguration.getBuildLogger()).thenReturn(mockBuildLogger);
    Mockito.when(mockBuildConfiguration.getContainerConfiguration())
        .thenReturn(mockContainerConfiguration);
    Mockito.when(mockContainerConfiguration.getCreationTime()).thenReturn(Instant.EPOCH);
    Mockito.when(mockContainerConfiguration.getEnvironmentMap()).thenReturn(ImmutableMap.of());
    Mockito.when(mockContainerConfiguration.getProgramArguments()).thenReturn(ImmutableList.of());
    Mockito.when(mockContainerConfiguration.getExposedPorts()).thenReturn(ImmutableList.of());
    Mockito.when(mockContainerConfiguration.getEntrypoint()).thenReturn(ImmutableList.of());

    Image<Layer> baseImage =
        Image.builder()
            .addEnvironment(ImmutableMap.of("BASE_ENV", "BASE_ENV_VALUE"))
            .addLabel("base.label", "base.label.value")
            .build();
    Mockito.when(mockPullAndCacheBaseImageLayerStep.getFuture())
        .thenReturn(Futures.immediateFuture(testCacheReadEntry));
    Mockito.when(mockPullAndCacheBaseImageLayersStep.getFuture())
        .thenReturn(
            Futures.immediateFuture(
                ImmutableList.of(
                    mockPullAndCacheBaseImageLayerStep,
                    mockPullAndCacheBaseImageLayerStep,
                    mockPullAndCacheBaseImageLayerStep)));
    Mockito.when(mockPullBaseImageStep.getFuture())
        .thenReturn(
            Futures.immediateFuture(
                new PullBaseImageStep.BaseImageWithAuthorization(baseImage, null)));
    Mockito.when(mockBuildAndCacheApplicationLayerStep.getFuture())
        .thenReturn(Futures.immediateFuture(testCacheReadEntry));
  }

  @Test
  public void test_validateAsyncDependencies() throws ExecutionException, InterruptedException {
    BuildImageStep buildImageStep =
        new BuildImageStep(
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
            mockBuildConfiguration,
            mockPullBaseImageStep,
            mockPullAndCacheBaseImageLayersStep,
            ImmutableList.of(
                mockBuildAndCacheApplicationLayerStep,
                mockBuildAndCacheApplicationLayerStep,
                mockBuildAndCacheApplicationLayerStep));
    Image<Layer> image = buildImageStep.getFuture().get().getFuture().get();
    Assert.assertEquals(
        testDescriptorDigest, image.getLayers().asList().get(0).getBlobDescriptor().getDigest());
  }

  @Test
  public void test_propagateBaseImageConfiguration()
      throws ExecutionException, InterruptedException {
    Mockito.when(mockContainerConfiguration.getEnvironmentMap())
        .thenReturn(ImmutableMap.of("MY_ENV", "MY_ENV_VALUE"));
    Mockito.when(mockContainerConfiguration.getLabels())
        .thenReturn(ImmutableMap.of("my.label", "my.label.value"));
    BuildImageStep buildImageStep =
        new BuildImageStep(
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
            mockBuildConfiguration,
            mockPullBaseImageStep,
            mockPullAndCacheBaseImageLayersStep,
            ImmutableList.of(
                mockBuildAndCacheApplicationLayerStep,
                mockBuildAndCacheApplicationLayerStep,
                mockBuildAndCacheApplicationLayerStep));
    Image<Layer> image = buildImageStep.getFuture().get().getFuture().get();
    Assert.assertEquals(
        ImmutableMap.of("BASE_ENV", "BASE_ENV_VALUE", "MY_ENV", "MY_ENV_VALUE"),
        image.getEnvironment());
    Assert.assertEquals(
        ImmutableMap.of("base.label", "base.label.value", "my.label", "my.label.value"),
        image.getLabels());
  }
}
